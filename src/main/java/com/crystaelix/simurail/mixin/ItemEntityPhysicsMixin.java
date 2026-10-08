package com.crystaelix.simurail.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.crystaelix.simurail.config.SimurailConfig;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Makes items ride along with moving trains by using Sable's built-in sublevel tracking system.
 * 
 * Items that are resting on a sublevel are anchored to its local frame via sable$setTrackingSubLevel,
 * so they move with the train at any speed without drift. Ground detection runs every tick for tracked
 * items (cheap check) and periodically for untracked items (expensive scan).
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityPhysicsMixin implements EntityMovementExtension {

	@Unique
	private int simurail$checkTick = 0;
	
	@Unique
	private static final double GROUND_CHECK_TOLERANCE = 0.25; // Generous margin for standing detection

	@Inject(method = "tick", at = @At("HEAD"))
	private void simurail$onTick(CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;

		if (!(self.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		if (!SimurailConfig.server().physics.lightweightEnabled.get()) {
			return;
		}

		// Skip if item is too young, removed, or in water
		if (self.isRemoved() || self.getAge() < 10 || self.isInWater()) {
			return;
		}

		// Get currently tracked sublevel
		ServerSubLevel currentTracking = (ServerSubLevel) sable$getTrackingSubLevel();
		boolean debugLog = SimurailConfig.server().physics.lightweightDebugLogging.get();

		// If already tracking, verify every tick (cheap: just check if still standing)
		if (currentTracking != null) {
			if (!self.verticalCollision || !self.onGround()) {
				// Item left the ground (jumped, pushed, or fell)
				if (debugLog) {
					org.slf4j.LoggerFactory.getLogger("Simurail").info(
						"[Lightweight Physics] Item {} left ground, untracking sublevel {} (reason: not onGround)",
						self.getId(), currentTracking.getUniqueId()
					);
				}
				sable$setTrackingSubLevel(null);
				return;
			}
			
			// Verify item is still above the tracked sublevel
			ServerSubLevel standingSubLevel = simurail$findStandingSubLevel(self, serverLevel);
			if (standingSubLevel != currentTracking) {
				// Item moved to a different sublevel or left the surface
				if (debugLog) {
					if (standingSubLevel == null) {
						org.slf4j.LoggerFactory.getLogger("Simurail").info(
							"[Lightweight Physics] Item {} lost sublevel contact, untracking {} (reason: no blocks below)",
							self.getId(), currentTracking.getUniqueId()
						);
					} else {
						org.slf4j.LoggerFactory.getLogger("Simurail").info(
							"[Lightweight Physics] Item {} switched from sublevel {} to {} (reason: moved between sublevels)",
							self.getId(), currentTracking.getUniqueId(), standingSubLevel.getUniqueId()
						);
					}
				}
				sable$setTrackingSubLevel(standingSubLevel);
			}
			return; // Already tracked and verified, done for this tick
		}

		// Not currently tracked: use throttled check to find new sublevels (saves CPU)
		simurail$checkTick++;
		int updateInterval = SimurailConfig.server().physics.lightweightUpdateInterval.get();
		if (simurail$checkTick % updateInterval != 0) {
			return;
		}

		// Check if item is standing on ground
		if (!self.verticalCollision || !self.onGround()) {
			return;
		}

		// Find the sublevel the item is standing on
		ServerSubLevel standingSubLevel = simurail$findStandingSubLevel(self, serverLevel);
		
		if (standingSubLevel != null) {
			if (debugLog) {
				org.slf4j.LoggerFactory.getLogger("Simurail").info(
					"[Lightweight Physics] Item {} starting to track sublevel {} at {} (reason: landed on sublevel)",
					self.getId(), standingSubLevel.getUniqueId(), self.position()
				);
			}
			sable$setTrackingSubLevel(standingSubLevel);
		}
	}

	/**
	 * Check blocks below the item to find if it's standing on a sublevel.
	 * Uses generous tolerance to avoid flickering when items rest exactly on surface.
	 */
	@Unique
	private ServerSubLevel simurail$findStandingSubLevel(ItemEntity item, ServerLevel level) {
		Vec3 pos = item.position();
		AABB bb = item.getBoundingBox();
		
		// Check area below the item with generous tolerance
		double minX = bb.minX;
		double minZ = bb.minZ;
		double maxX = bb.maxX;
		double maxZ = bb.maxZ;
		double checkY = bb.minY - GROUND_CHECK_TOLERANCE;

		// Sample positions below the item
		int samples = 0;
		ServerSubLevel foundSubLevel = null;
		
		for (double x = minX; x <= maxX; x += 0.125) {
			for (double z = minZ; z <= maxZ; z += 0.125) {
				BlockPos checkPos = BlockPos.containing(x, checkY, z);
				
				// Check if this position is within a sublevel
				ServerSubLevel subLevel = (ServerSubLevel) Sable.HELPER.getContaining(level, checkPos);
				if (subLevel != null && !subLevel.isRemoved()) {
					samples++;
					if (foundSubLevel == null) {
						foundSubLevel = subLevel;
					} else if (foundSubLevel != subLevel) {
						// Item is between two sublevels, keep tracking current one if any
						ServerSubLevel currentTracking = (ServerSubLevel) sable$getTrackingSubLevel();
						if (currentTracking == subLevel || currentTracking == foundSubLevel) {
							// Prefer the currently tracked sublevel to avoid flicker
							return currentTracking;
						}
						// Unclear which to track, return the first found
						return foundSubLevel;
					}
				}
			}
		}
		
		// Require at least one solid sublevel block below the item
		return samples > 0 ? foundSubLevel : null;
	}
}
