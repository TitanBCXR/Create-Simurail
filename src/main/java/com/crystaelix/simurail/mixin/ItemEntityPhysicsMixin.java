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
 * Instead of creating custom physics bodies, this mixin detects when an item is standing on
 * a sublevel (train) and calls sable$setTrackingSubLevel to make it ride along.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityPhysicsMixin implements EntityMovementExtension {

	@Unique
	private int simurail$checkTick = 0;

	@Inject(method = "tick", at = @At("HEAD"))
	private void simurail$onTick(CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;

		if (!(self.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		if (!SimurailConfig.server().physics.lightweightEnabled.get()) {
			return;
		}

		// Only check for sublevels occasionally to reduce overhead
		simurail$checkTick++;
		int updateInterval = SimurailConfig.server().physics.lightweightUpdateInterval.get();
		if (simurail$checkTick % updateInterval != 0) {
			return;
		}

		// Skip if item is too young, removed, or in water
		if (self.isRemoved() || self.getAge() < 10 || self.isInWater()) {
			return;
		}

		// Check if item is standing on ground (has downward collision)
		if (!self.verticalCollision || !self.onGround()) {
			return;
		}

		// Find the sublevel the item is standing on
		ServerSubLevel standingSubLevel = simurail$findStandingSubLevel(self, serverLevel);
		
		// Get currently tracked sublevel
		ServerSubLevel currentTracking = (ServerSubLevel) sable$getTrackingSubLevel();

		boolean debugLog = SimurailConfig.server().physics.lightweightDebugLogging.get();

		// Update tracking if needed
		if (standingSubLevel != null && standingSubLevel != currentTracking) {
			if (debugLog) {
				org.slf4j.LoggerFactory.getLogger("Simurail").info(
					"[Lightweight Physics] Item {} starting to track sublevel {} at {}",
					self.getId(), standingSubLevel.getUniqueId(), self.position()
				);
			}
			sable$setTrackingSubLevel(standingSubLevel);
		} else if (standingSubLevel == null && currentTracking != null) {
			if (debugLog) {
				org.slf4j.LoggerFactory.getLogger("Simurail").info(
					"[Lightweight Physics] Item {} stopped tracking sublevel {}",
					self.getId(), currentTracking.getUniqueId()
				);
			}
			sable$setTrackingSubLevel(null);
		}
	}

	/**
	 * Check blocks slightly below the item to find if it's standing on a sublevel.
	 */
	@Unique
	private ServerSubLevel simurail$findStandingSubLevel(ItemEntity item, ServerLevel level) {
		Vec3 pos = item.position();
		AABB bb = item.getBoundingBox();
		
		// Check a small area below the item
		double minX = bb.minX;
		double minZ = bb.minZ;
		double maxX = bb.maxX;
		double maxZ = bb.maxZ;
		double checkY = bb.minY - 0.1; // Slightly below item

		// Sample a few positions below the item
		int samples = 0;
		ServerSubLevel foundSubLevel = null;
		
		for (double x = minX; x <= maxX; x += 0.125) {
			for (double z = minZ; z <= maxZ; z += 0.125) {
				BlockPos checkPos = BlockPos.containing(x, checkY, z);
				
				// Check if this position is within a sublevel
				// If getContaining returns a sublevel, there's a block at that position
				ServerSubLevel subLevel = (ServerSubLevel) Sable.HELPER.getContaining(level, checkPos);
				if (subLevel != null && !subLevel.isRemoved()) {
					samples++;
					if (foundSubLevel == null) {
						foundSubLevel = subLevel;
					} else if (foundSubLevel != subLevel) {
						// Item is between two sublevels, unclear which to track
						return null;
					}
				}
			}
		}
		
		// Require at least one solid sublevel block below the item
		return samples > 0 ? foundSubLevel : null;
	}
}
