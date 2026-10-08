package com.crystaelix.simurail.api.physics;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import com.crystaelix.simurail.config.SimurailConfig;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Lightweight physics body that can attach to nearby sublevels without creating its own.
 * Designed for small objects (items, entities, cargo) that need physics interaction
 * with moving trains/bogeys but don't justify the overhead of a full sublevel.
 */
public class LightweightPhysicsBody {

	private final ServerLevel level;
	private final Vector3d halfExtents;
	private final double baseMass;

	private final Vector3d position = new Vector3d();
	private final Vector3d velocity = new Vector3d();
	private final Vector3d lastPosition = new Vector3d();

	private ServerSubLevel attachedSubLevel;
	private AttachableBoxPhysicsObject physicsObject;

	private boolean sleeping = false;
	private int ticksSinceActive = 0;

	public LightweightPhysicsBody(ServerLevel level, Vector3dc halfExtents, double mass) {
		this.level = level;
		this.halfExtents = new Vector3d(halfExtents);
		this.baseMass = mass;
	}

	/**
	 * Update the physics body position and check if it should be activated/deactivated.
	 * 
	 * @param worldPos The current world position
	 * @param nearestSubLevel The nearest active sublevel, or null if none nearby
	 * @return true if physics is active, false otherwise
	 */
	public boolean update(Vec3 worldPos, ServerSubLevel nearestSubLevel) {
		JOMLConversion.toJOML(worldPos, position);

		if (!SimurailConfig.server().physics.lightweightEnabled.get()) {
			deactivate();
			return false;
		}

		// Check if we should attach to a new sublevel
		if (nearestSubLevel != null && nearestSubLevel != attachedSubLevel) {
			deactivate();
			activate(nearestSubLevel);
		}

		// Update physics object if active
		if (isActive()) {
			updatePhysicsState();

			// Check if we should sleep
			if (velocity.lengthSquared() < Math.pow(SimurailConfig.server().physics.lightweightSleepVelocity.get(), 2)) {
				ticksSinceActive++;
				if (ticksSinceActive > 20) {
					sleeping = true;
				}
			} else {
				ticksSinceActive = 0;
				sleeping = false;
			}

			// Deactivate if sublevel is gone or too far
			if (attachedSubLevel == null || attachedSubLevel.isRemoved()) {
				deactivate();
				return false;
			}

			double distSq = position.distanceSquared(
				attachedSubLevel.logicalPose().position().x(),
				attachedSubLevel.logicalPose().position().y(),
				attachedSubLevel.logicalPose().position().z()
			);
			double maxDist = SimurailConfig.server().physics.lightweightActivationRadius.get() * 1.5;
			if (distSq > maxDist * maxDist) {
				deactivate();
				return false;
			}
		}

		return isActive();
	}

	/**
	 * Activate physics by attaching to the given sublevel.
	 */
	private void activate(ServerSubLevel subLevel) {
		if (physicsObject != null) {
			deactivate();
		}

		attachedSubLevel = subLevel;

		// Create physics object
		Pose3d pose = new Pose3d();
		pose.position().set(position);
		pose.orientation().identity();

		double mass = baseMass * SimurailConfig.server().physics.lightweightMassScale.get();
		physicsObject = new AttachableBoxPhysicsObject(subLevel, pose, halfExtents, mass);

		SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.require(level);
		physics.addObject(physicsObject);

		sleeping = false;
		ticksSinceActive = 0;
	}

	/**
	 * Deactivate physics and detach from sublevel.
	 */
	public void deactivate() {
		if (physicsObject != null) {
			SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.require(level);
			physics.removeObject(physicsObject);
			physicsObject = null;
		}
		attachedSubLevel = null;
		sleeping = false;
	}

	/**
	 * Update velocity from physics simulation.
	 */
	private void updatePhysicsState() {
		if (physicsObject == null || physicsObject.isRemoved()) {
			return;
		}

		// Update pose from physics
		physicsObject.updatePose();
		position.set(physicsObject.getPose().position());

		// Calculate velocity
		velocity.set(position).sub(lastPosition);
		lastPosition.set(position);

		// Get velocity from rigid body if available
		RigidBodyHandle handle = RigidBodyHandle.of(level, physicsObject);
		if (handle != null) {
			handle.getLinearVelocity(velocity);
		}
	}

	/**
	 * Apply an impulse to this physics body at its center.
	 */
	public void applyImpulse(Vector3dc impulse) {
		if (!isActive()) {
			velocity.add(impulse);
			return;
		}

		RigidBodyHandle handle = RigidBodyHandle.of(level, physicsObject);
		if (handle != null) {
			// Apply impulse at center of mass
			handle.applyImpulseAtPoint(position, new Vector3d(impulse));
		}
	}

	/**
	 * Apply an impulse at a specific point.
	 */
	public void applyImpulseAtPoint(Vector3dc point, Vector3dc impulse) {
		if (!isActive()) {
			velocity.add(impulse);
			return;
		}

		RigidBodyHandle handle = RigidBodyHandle.of(level, physicsObject);
		if (handle != null) {
			handle.applyImpulseAtPoint(new Vector3d(point), new Vector3d(impulse));
		}
	}

	public boolean isActive() {
		return physicsObject != null && !physicsObject.isRemoved();
	}

	public boolean isSleeping() {
		return sleeping;
	}

	public Vector3dc getPosition() {
		return position;
	}

	public Vector3dc getVelocity() {
		return velocity;
	}

	public ServerSubLevel getAttachedSubLevel() {
		return attachedSubLevel;
	}

	/**
	 * Get the velocity as a Minecraft Vec3.
	 */
	public Vec3 getVelocityVec3() {
		return new Vec3(velocity.x, velocity.y, velocity.z);
	}

	/**
	 * Wake up the physics body if it's sleeping.
	 */
	public void wakeUp() {
		sleeping = false;
		ticksSinceActive = 0;
	}
}
