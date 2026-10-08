package com.crystaelix.simurail.api.physics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3d;

import com.crystaelix.simurail.config.SimurailConfig;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Manages lightweight physics bodies for entities and items.
 * Handles activation/deactivation based on proximity to active physics sublevels.
 */
public class LightweightPhysicsManager {

	private static final Map<ServerLevel, LightweightPhysicsManager> INSTANCES = new HashMap<>();

	private final ServerLevel level;
	private final Map<UUID, LightweightPhysicsBody> physicsBodyMap = new HashMap<>();
	private final List<ServerSubLevel> activeSubLevels = new ArrayList<>();

	// Reusable vectors to reduce allocations
	private final Vector3d tempPos = new Vector3d();

	private int updateTick = 0;

	private LightweightPhysicsManager(ServerLevel level) {
		this.level = level;
	}

	/**
	 * Get or create the manager for a level.
	 */
	public static LightweightPhysicsManager get(ServerLevel level) {
		return INSTANCES.computeIfAbsent(level, LightweightPhysicsManager::new);
	}

	/**
	 * Register a sublevel as active for lightweight physics attachment.
	 * Call this when a bogey/train creates or moves its sublevel.
	 */
	public void registerActiveSubLevel(ServerSubLevel subLevel) {
		if (subLevel != null && !subLevel.isRemoved() && !activeSubLevels.contains(subLevel)) {
			activeSubLevels.add(subLevel);
		}
	}

	/**
	 * Unregister a sublevel.
	 */
	public void unregisterActiveSubLevel(ServerSubLevel subLevel) {
		activeSubLevels.remove(subLevel);
	}

	/**
	 * Get or create a lightweight physics body for an entity.
	 */
	public LightweightPhysicsBody getOrCreate(Entity entity, org.joml.Vector3dc halfExtents, double mass) {
		return physicsBodyMap.computeIfAbsent(entity.getUUID(), uuid ->
			new LightweightPhysicsBody((ServerLevel) entity.level(), halfExtents, mass)
		);
	}

	/**
	 * Remove a physics body.
	 */
	public void remove(Entity entity) {
		LightweightPhysicsBody body = physicsBodyMap.remove(entity.getUUID());
		if (body != null) {
			body.deactivate();
		}
	}

	/**
	 * Tick all managed physics bodies. Call this once per server tick.
	 */
	public void tick() {
		if (!SimurailConfig.server().physics.lightweightEnabled.get()) {
			// Deactivate all if disabled
			physicsBodyMap.values().forEach(LightweightPhysicsBody::deactivate);
			return;
		}

		updateTick++;

		// Clean up removed sublevels
		activeSubLevels.removeIf(sl -> sl == null || sl.isRemoved());

		int updateInterval = SimurailConfig.server().physics.lightweightUpdateInterval.get();
		int maxActive = SimurailConfig.server().physics.lightweightMaxActive.get();

		// Count active bodies
		long activeCount = physicsBodyMap.values().stream().filter(LightweightPhysicsBody::isActive).count();

		// Update bodies in a staggered fashion
		List<Map.Entry<UUID, LightweightPhysicsBody>> entries = new ArrayList<>(physicsBodyMap.entrySet());
		for (int i = 0; i < entries.size(); i++) {
			// Stagger updates across multiple ticks
			if ((i + updateTick) % updateInterval != 0) {
				continue;
			}

			Map.Entry<UUID, LightweightPhysicsBody> entry = entries.get(i);
			LightweightPhysicsBody body = entry.getValue();

			// If at max capacity and this body isn't active, skip it
			if (activeCount >= maxActive && !body.isActive()) {
				continue;
			}

			// Find nearest sublevel
			ServerSubLevel nearest = findNearestSubLevel(body.getPosition());

			// Update body
			boolean wasActive = body.isActive();
			body.update(new Vec3(body.getPosition().x(), body.getPosition().y(), body.getPosition().z()), nearest);
			boolean isActive = body.isActive();

			// Update active count
			if (isActive && !wasActive) {
				activeCount++;
			} else if (!isActive && wasActive) {
				activeCount--;
			}
		}

		// Clean up old entries
		physicsBodyMap.entrySet().removeIf(entry -> {
			LightweightPhysicsBody body = entry.getValue();
			// Remove if inactive for too long
			return !body.isActive() && body.isSleeping();
		});
	}

	/**
	 * Find the nearest active sublevel to a position.
	 */
	private ServerSubLevel findNearestSubLevel(org.joml.Vector3dc position) {
		double activationRadius = SimurailConfig.server().physics.lightweightActivationRadius.get();
		double minDistSq = activationRadius * activationRadius;
		ServerSubLevel nearest = null;

		// Check registered sublevels first (faster)
		for (ServerSubLevel subLevel : activeSubLevels) {
			if (subLevel == null || subLevel.isRemoved()) {
				continue;
			}

			double distSq = position.distanceSquared(
				subLevel.logicalPose().position().x(),
				subLevel.logicalPose().position().y(),
				subLevel.logicalPose().position().z()
			);

			if (distSq < minDistSq) {
				minDistSq = distSq;
				nearest = subLevel;
			}
		}

		// If no registered sublevel found, check if there's one at the position
		if (nearest == null) {
			Vector3d pos = new Vector3d(position);
			ServerSubLevel containing = (ServerSubLevel) Sable.HELPER.getContaining(level, 
				JOMLConversion.toMojang(pos));
			if (containing != null && !containing.isRemoved()) {
				// Make sure it's within activation radius
				double distSq = position.distanceSquared(
					containing.logicalPose().position().x(),
					containing.logicalPose().position().y(),
					containing.logicalPose().position().z()
				);
				if (distSq < activationRadius * activationRadius) {
					nearest = containing;
					// Register it for future lookups
					registerActiveSubLevel(containing);
				}
			}
		}

		return nearest;
	}

	/**
	 * Update a specific physics body with a new position.
	 * Call this from entity tick.
	 */
	public boolean update(UUID entityId, Vec3 position) {
		LightweightPhysicsBody body = physicsBodyMap.get(entityId);
		if (body == null) {
			return false;
		}

		JOMLConversion.toJOML(position, tempPos);
		ServerSubLevel nearest = findNearestSubLevel(tempPos);
		return body.update(position, nearest);
	}

	/**
	 * Get active body count for debugging.
	 */
	public int getActiveCount() {
		return (int) physicsBodyMap.values().stream().filter(LightweightPhysicsBody::isActive).count();
	}

	/**
	 * Get total body count for debugging.
	 */
	public int getTotalCount() {
		return physicsBodyMap.size();
	}

	/**
	 * Clear all physics bodies. Call on level unload.
	 */
	public static void clearLevel(ServerLevel level) {
		LightweightPhysicsManager manager = INSTANCES.remove(level);
		if (manager != null) {
			manager.physicsBodyMap.values().forEach(LightweightPhysicsBody::deactivate);
			manager.physicsBodyMap.clear();
			manager.activeSubLevels.clear();
		}
	}
}
