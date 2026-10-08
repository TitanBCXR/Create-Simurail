package com.crystaelix.simurail.mixin;

import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.crystaelix.simurail.api.physics.ILightweightPhysicsEntity;
import com.crystaelix.simurail.api.physics.LightweightPhysicsBody;
import com.crystaelix.simurail.api.physics.LightweightPhysicsManager;
import com.crystaelix.simurail.config.SimurailConfig;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Adds lightweight physics to item entities so they can interact with moving trains.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityPhysicsMixin implements ILightweightPhysicsEntity {

	@Unique
	private LightweightPhysicsBody simurail$physicsBody;

	@Unique
	private int simurail$physicsTick = 0;

	@Override
	public LightweightPhysicsBody simurail$getPhysicsBody() {
		return simurail$physicsBody;
	}

	@Override
	public void simurail$setPhysicsBody(LightweightPhysicsBody body) {
		this.simurail$physicsBody = body;
	}

	@Override
	public org.joml.Vector3dc simurail$getPhysicsHalfExtents() {
		return new Vector3d(0.125, 0.125, 0.125);
	}

	@Override
	public double simurail$getPhysicsMass() {
		ItemEntity self = (ItemEntity) (Object) this;
		return 0.25 * self.getItem().getCount();
	}

	@Override
	public boolean simurail$shouldHavePhysics() {
		ItemEntity self = (ItemEntity) (Object) this;
		return !self.isRemoved() && 
		       self.level() instanceof ServerLevel &&
		       SimurailConfig.server().physics.lightweightEnabled.get() &&
		       !self.isInWater() && // Disable in water for now
		       self.getAge() > 10; // Wait a moment after spawning
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void simurail$onTick(CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;

		if (!(self.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		if (!simurail$shouldHavePhysics()) {
			// Clean up physics body if conditions changed
			if (simurail$physicsBody != null) {
				LightweightPhysicsManager.get(serverLevel).remove(self);
				simurail$physicsBody = null;
			}
			return;
		}

		simurail$physicsTick++;

		// Create physics body if needed
		if (simurail$physicsBody == null) {
			LightweightPhysicsManager manager = LightweightPhysicsManager.get(serverLevel);
			org.joml.Vector3dc halfExtents = simurail$getPhysicsHalfExtents();
			simurail$physicsBody = manager.getOrCreate(self, halfExtents, simurail$getPhysicsMass());
		}

		// Update physics every few ticks
		int updateInterval = SimurailConfig.server().physics.lightweightUpdateInterval.get();
		if (simurail$physicsTick % updateInterval == 0) {
			Vec3 pos = self.position();
			boolean active = LightweightPhysicsManager.get(serverLevel).update(self.getUUID(), pos);

			// If physics is active, apply the physics velocity to the entity
			if (active && simurail$physicsBody != null) {
				Vec3 physicsVel = simurail$physicsBody.getVelocityVec3();

				// Blend physics velocity with current velocity for smooth transition
				Vec3 currentVel = self.getDeltaMovement();
				Vec3 blendedVel = currentVel.scale(0.3).add(physicsVel.scale(0.7));

				self.setDeltaMovement(blendedVel);

				// Wake up if item was thrown or moved
				if (currentVel.lengthSqr() > 0.01) {
					simurail$physicsBody.wakeUp();
				}
			}
		}
	}

	@Inject(method = "remove", at = @At("HEAD"))
	private void simurail$onRemove(CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		if (self.level() instanceof ServerLevel serverLevel && simurail$physicsBody != null) {
			LightweightPhysicsManager.get(serverLevel).remove(self);
			simurail$physicsBody = null;
		}
	}
}
