package com.crystaelix.simurail.api.physics;

import org.joml.Vector3dc;

/**
 * Interface for entities that want to participate in lightweight physics.
 * Implement this on custom entities or apply via mixin to vanilla entities.
 */
public interface ILightweightPhysicsEntity {

	/**
	 * Get the lightweight physics body for this entity, or null if not initialized.
	 */
	LightweightPhysicsBody simurail$getPhysicsBody();

	/**
	 * Set the lightweight physics body for this entity.
	 */
	void simurail$setPhysicsBody(LightweightPhysicsBody body);

	/**
	 * Get the half-extents (half-width, half-height, half-depth) for the physics collision box.
	 * Default implementation uses entity bounding box.
	 */
	default Vector3dc simurail$getPhysicsHalfExtents() {
		return new org.joml.Vector3d(0.25, 0.125, 0.25);
	}

	/**
	 * Get the mass for this entity's physics body.
	 * Default is 1.0 kg.
	 */
	default double simurail$getPhysicsMass() {
		return 1.0;
	}

	/**
	 * Whether this entity should have lightweight physics enabled.
	 * Can be used to disable physics for specific entities or in certain conditions.
	 */
	default boolean simurail$shouldHavePhysics() {
		return true;
	}
}
