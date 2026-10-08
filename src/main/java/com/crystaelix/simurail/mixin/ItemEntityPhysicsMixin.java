package com.crystaelix.simurail.mixin;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.crystaelix.simurail.config.SimurailConfig;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Implements true local-frame anchoring for items on moving trains.
 * 
 * <p>When an item comes to rest on a SubLevel, we record its position and velocity in the
 * SubLevel's LOCAL coordinate frame. Each tick, we compute the world position by transforming
 * the local position through the SubLevel's current pose, and set the entity position directly.
 * This prevents vanilla physics from causing drift or glitches at any train speed or acceleration.
 * 
 * <p>Investigation of Sable's tracking: {@code sable$setTrackingSubLevel} only sets a reference
 * for collision detection. Sable uses a separate "plot position" system (sable$setPlotPosition)
 * for client-side interpolation, but that doesn't handle server-side anchoring. We implement
 * our own local-frame system here.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityPhysicsMixin implements EntityMovementExtension {

	@Unique
	private ServerSubLevel simurail$anchoredSubLevel = null;
	
	@Unique
	private Vec3 simurail$localPosition = null;
	
	@Unique
	private Vec3 simurail$localVelocity = Vec3.ZERO;
	
	@Unique
	private static final double GROUND_CHECK_TOLERANCE = 0.05;
	
	@Unique
	private static final double EXTERNAL_FORCE_THRESHOLD = 0.5; // m/s threshold for release
	
	@Unique
	private static final double LOCAL_FRICTION = 0.98;
	
	@Unique
	private static final double LOCAL_GRAVITY = 0.04;

	@Inject(method = "tick", at = @At("HEAD"))
	private void simurail$onTickHead(CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;

		if (!(self.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		if (!SimurailConfig.server().physics.lightweightEnabled.get()) {
			// Clear any anchoring if physics is disabled
			if (simurail$anchoredSubLevel != null) {
				simurail$releaseAnchor(self, "physics disabled");
			}
			return;
		}

		// Skip if item is too young or removed
		if (self.isRemoved() || self.getAge() < 10) {
			return;
		}

		// Apply fluid current physics
		if (SimurailConfig.server().physics.fluidsCurrentsEnabled.get()) {
			simurail$applyFluidCurrents(self);
		}

		// Skip sublevel anchoring if in water
		if (self.isInWater()) {
			if (simurail$anchoredSubLevel != null) {
				simurail$releaseAnchor(self, "in water");
			}
			return;
		}

		boolean debugLog = SimurailConfig.server().physics.lightweightDebugLogging.get();

		// Handle anchored state
		if (simurail$anchoredSubLevel != null) {
			simurail$updateAnchoredItem(self, serverLevel, debugLog);
			return;
		}

		// Not anchored: check if item should be anchored
		if (self.verticalCollision && self.onGround()) {
			ServerSubLevel standingSubLevel = simurail$findStandingSubLevel(self, serverLevel);
			if (standingSubLevel != null) {
				simurail$anchorToSubLevel(self, standingSubLevel, debugLog);
			}
		}
	}

	/**
	 * Update an anchored item: transform local position to world, override vanilla physics.
	 */
	@Unique
	private void simurail$updateAnchoredItem(ItemEntity item, ServerLevel level, boolean debugLog) {
		// Check if sublevel still exists
		if (simurail$anchoredSubLevel.isRemoved()) {
			simurail$releaseAnchor(item, "sublevel removed");
			return;
		}

		// Get current pose
		Pose3d pose = simurail$anchoredSubLevel.logicalPose();
		
		// Transform local position to world
		Vec3 worldPos = simurail$transformLocalToWorld(simurail$localPosition, pose);
		
		// Check if block beneath still exists in local space
		// Transform local position down slightly to check block below
		Vec3 localCheckPos = simurail$localPosition.add(0, -GROUND_CHECK_TOLERANCE, 0);
		Vec3 worldCheckPos = simurail$transformLocalToWorld(localCheckPos, pose);
		BlockPos checkBlockPos = BlockPos.containing(worldCheckPos);
		
		ServerSubLevel subLevelAtCheck = (ServerSubLevel) Sable.HELPER.getContaining(level, checkBlockPos);
		if (subLevelAtCheck != simurail$anchoredSubLevel) {
			// Block beneath is gone in local frame
			simurail$releaseAnchor(item, "block beneath removed");
			return;
		}
		
		// Check for external forces (explosion, player push, etc.)
		Vec3 expectedWorldVel = simurail$getExpectedWorldVelocity(pose);
		Vec3 actualVel = item.getDeltaMovement();
		double velDiff = actualVel.subtract(expectedWorldVel).length();
		if (velDiff > EXTERNAL_FORCE_THRESHOLD) {
			simurail$releaseAnchor(item, String.format("external force (%.2f m/s)", velDiff));
			return;
		}
		
		// Apply friction and gravity in local space
		simurail$localVelocity = simurail$localVelocity.multiply(LOCAL_FRICTION, 1.0, LOCAL_FRICTION);
		simurail$localVelocity = simurail$localVelocity.add(0, -LOCAL_GRAVITY, 0);
		
		// Clamp local velocity to prevent buildup
		if (Math.abs(simurail$localVelocity.x) < 0.001) simurail$localVelocity = new Vec3(0, simurail$localVelocity.y, simurail$localVelocity.z);
		if (Math.abs(simurail$localVelocity.y) < 0.001) simurail$localVelocity = new Vec3(simurail$localVelocity.x, 0, simurail$localVelocity.z);
		if (Math.abs(simurail$localVelocity.z) < 0.001) simurail$localVelocity = new Vec3(simurail$localVelocity.x, simurail$localVelocity.y, 0);
		
		// Update local position
		simurail$localPosition = simurail$localPosition.add(simurail$localVelocity);
		
		// Transform to world and set entity position
		worldPos = simurail$transformLocalToWorld(simurail$localPosition, pose);
		Vec3 worldVel = simurail$getExpectedWorldVelocity(pose);
		
		// Set position and old position for smooth interpolation
		item.xOld = item.getX();
		item.yOld = item.getY();
		item.zOld = item.getZ();
		
		item.setPos(worldPos);
		item.setDeltaMovement(worldVel);
		
		// Set Sable tracking for collision detection
		sable$setTrackingSubLevel(simurail$anchoredSubLevel);
	}

	/**
	 * Anchor an item to a sublevel's local frame.
	 */
	@Unique
	private void simurail$anchorToSubLevel(ItemEntity item, ServerSubLevel subLevel, boolean debugLog) {
		Pose3d pose = subLevel.logicalPose();
		
		// Transform world position to local
		simurail$localPosition = simurail$transformWorldToLocal(item.position(), pose);
		
		// Transform world velocity to local (remove sublevel's motion)
		simurail$localVelocity = simurail$transformVelocityWorldToLocal(item.getDeltaMovement(), pose);
		
		simurail$anchoredSubLevel = subLevel;
		
		if (debugLog) {
			org.slf4j.LoggerFactory.getLogger("Simurail").info(
				"[Lightweight Physics] Item {} anchored to sublevel {} at local pos {} (world {})",
				item.getId(), subLevel.getUniqueId(), simurail$localPosition, item.position()
			);
		}
		
		// Set Sable tracking
		sable$setTrackingSubLevel(subLevel);
	}

	/**
	 * Release an item from its anchor.
	 */
	@Unique
	private void simurail$releaseAnchor(ItemEntity item, String reason) {
		if (simurail$anchoredSubLevel == null) {
			return;
		}
		
		boolean debugLog = SimurailConfig.server().physics.lightweightDebugLogging.get();
		
		if (debugLog) {
			org.slf4j.LoggerFactory.getLogger("Simurail").info(
				"[Lightweight Physics] Item {} released from sublevel {} (reason: {})",
				item.getId(), simurail$anchoredSubLevel.getUniqueId(), reason
			);
		}
		
		// Inherit world velocity at the moment of release
		if (simurail$anchoredSubLevel != null && !simurail$anchoredSubLevel.isRemoved()) {
			Pose3d pose = simurail$anchoredSubLevel.logicalPose();
			Vec3 inheritedVel = simurail$getExpectedWorldVelocity(pose);
			item.setDeltaMovement(inheritedVel);
		}
		
		simurail$anchoredSubLevel = null;
		simurail$localPosition = null;
		simurail$localVelocity = Vec3.ZERO;
		
		// Clear Sable tracking
		sable$setTrackingSubLevel(null);
	}

	/**
	 * Transform a position from local SubLevel space to world space.
	 */
	@Unique
	private Vec3 simurail$transformLocalToWorld(Vec3 localPos, Pose3d pose) {
		// Convert Vec3 to Vector3d for math
		Vector3d local = new Vector3d(localPos.x, localPos.y, localPos.z);
		
		// Apply rotation
		Quaterniond rotation = pose.orientation();
		Vector3d rotated = rotation.transform(local, new Vector3d());
		
		// Apply translation
		Vector3d translation = pose.position();
		rotated.add(translation);
		
		return new Vec3(rotated.x, rotated.y, rotated.z);
	}

	/**
	 * Transform a position from world space to local SubLevel space.
	 */
	@Unique
	private Vec3 simurail$transformWorldToLocal(Vec3 worldPos, Pose3d pose) {
		// Subtract translation
		Vector3d translation = pose.position();
		Vector3d relative = new Vector3d(worldPos.x - translation.x, worldPos.y - translation.y, worldPos.z - translation.z);
		
		// Apply inverse rotation
		Quaterniond rotation = pose.orientation();
		Quaterniond invRotation = rotation.invert(new Quaterniond());
		Vector3d local = invRotation.transform(relative, new Vector3d());
		
		return new Vec3(local.x, local.y, local.z);
	}

	/**
	 * Transform velocity from world space to local space.
	 */
	@Unique
	private Vec3 simurail$transformVelocityWorldToLocal(Vec3 worldVel, Pose3d pose) {
		// Velocity is just rotation (no translation)
		Quaterniond rotation = pose.orientation();
		Quaterniond invRotation = rotation.invert(new Quaterniond());
		Vector3d worldV = new Vector3d(worldVel.x, worldVel.y, worldVel.z);
		Vector3d localV = invRotation.transform(worldV, new Vector3d());
		
		return new Vec3(localV.x, localV.y, localV.z);
	}

	/**
	 * Get the expected world velocity for the anchored item (SubLevel velocity + local velocity).
	 */
	@Unique
	private Vec3 simurail$getExpectedWorldVelocity(Pose3d pose) {
		// For now, just use the local velocity rotated to world space
		// (SubLevel velocity would need to be computed from pose delta, which we don't track)
		Quaterniond rotation = pose.orientation();
		Vector3d localV = new Vector3d(simurail$localVelocity.x, simurail$localVelocity.y, simurail$localVelocity.z);
		Vector3d worldV = rotation.transform(localV, new Vector3d());
		
		return new Vec3(worldV.x, worldV.y, worldV.z);
	}

	/**
	 * Check blocks below the item to find if it's standing on a sublevel.
	 */
	@Unique
	private ServerSubLevel simurail$findStandingSubLevel(ItemEntity item, ServerLevel level) {
		Vec3 pos = item.position();
		AABB bb = item.getBoundingBox();
		
		double minX = bb.minX;
		double minZ = bb.minZ;
		double maxX = bb.maxX;
		double maxZ = bb.maxZ;
		double checkY = bb.minY - GROUND_CHECK_TOLERANCE;

		// Sample positions below the item
		ServerSubLevel foundSubLevel = null;
		
		for (double x = minX; x <= maxX; x += 0.125) {
			for (double z = minZ; z <= maxZ; z += 0.125) {
				BlockPos checkPos = BlockPos.containing(x, checkY, z);
				
				ServerSubLevel subLevel = (ServerSubLevel) Sable.HELPER.getContaining(level, checkPos);
				if (subLevel != null && !subLevel.isRemoved()) {
					if (foundSubLevel == null) {
						foundSubLevel = subLevel;
					} else if (foundSubLevel != subLevel) {
						// Item is between two sublevels, prefer current if any
						if (simurail$anchoredSubLevel == subLevel || simurail$anchoredSubLevel == foundSubLevel) {
							return simurail$anchoredSubLevel;
						}
						return foundSubLevel;
					}
				}
			}
		}
		
		return foundSubLevel;
	}

	/**
	 * Apply fluid current forces to items in water/lava.
	 */
	@Unique
	private void simurail$applyFluidCurrents(ItemEntity item) {
		if (!item.isInFluidType()) {
			return;
		}

		Vec3 pos = item.position();
		BlockPos blockPos = BlockPos.containing(pos);
		net.minecraft.world.level.material.FluidState fluidState = item.level().getFluidState(blockPos);

		if (fluidState.isEmpty()) {
			return;
		}

		Vec3 flow = fluidState.getFlow(item.level(), blockPos);
		
		if (flow.lengthSqr() < 0.001) {
			return;
		}

		float currentStrength = SimurailConfig.server().physics.fluidsCurrentStrength.get().floatValue();
		float buoyancy = SimurailConfig.server().physics.fluidsBuoyancy.get().floatValue();

		Vec3 currentForce = flow.scale(currentStrength);
		item.setDeltaMovement(item.getDeltaMovement().add(currentForce));

		if (fluidState.is(net.minecraft.tags.FluidTags.WATER)) {
			double surfaceY = blockPos.getY() + fluidState.getHeight(item.level(), blockPos);
			if (pos.y < surfaceY) {
				double buoyancyForce = buoyancy * (surfaceY - pos.y);
				item.setDeltaMovement(item.getDeltaMovement().add(0, Math.min(buoyancyForce, 0.1), 0));
			}
		} else if (fluidState.is(net.minecraft.tags.FluidTags.LAVA)) {
			Vec3 vel = item.getDeltaMovement();
			item.setDeltaMovement(vel.multiply(0.95, 0.85, 0.95));
		}
	}
}
