package com.crystaelix.simurail.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3d;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-only occupants that debris parts around: entities plus nearby Sable
 * SubLevels (trains). Cubes never push these; they only get displaced visually.
 */
@OnlyIn(Dist.CLIENT)
final class DebrisOccupants {

	static final int MAX_ENTITIES = 32;
	static final int MAX_SUBLEVELS = 12;
	static final int MAX_PAIR_CHECKS = 256;
	static final int MAX_WAKES = 24;
	static final int WAKE_LIFE = 40;

	static final class Occupant {
		final AABB box;
		final double cx;
		final double cy;
		final double cz;
		final double vx;
		final double vz;
		final double speed;

		Occupant(AABB box, double vx, double vz) {
			this.box = box;
			this.cx = (box.minX + box.maxX) * 0.5;
			this.cy = (box.minY + box.maxY) * 0.5;
			this.cz = (box.minZ + box.maxZ) * 0.5;
			this.vx = vx;
			this.vz = vz;
			this.speed = Math.sqrt(vx * vx + vz * vz);
		}
	}

	static final class Wake {
		double x;
		double z;
		double dirX;
		double dirZ;
		float strength;
		int age;

		Wake(double x, double z, double dirX, double dirZ, float strength) {
			this.x = x;
			this.z = z;
			this.dirX = dirX;
			this.dirZ = dirZ;
			this.strength = strength;
			this.age = 0;
		}
	}

	final List<Occupant> occupants = new ArrayList<>();
	final Map<Long, ArrayList<Occupant>> cells = new HashMap<>();
	final List<Wake> wakes = new ArrayList<>();

	void gather(Level level, Vec3 playerPos, int radius) {
		occupants.clear();
		cells.clear();
		AABB search = new AABB(playerPos, playerPos).inflate(radius, 6.0, radius);

		List<Entity> entities = level.getEntities((Entity) null, search);
		int taken = 0;
		for (Entity entity : entities) {
			if (taken >= MAX_ENTITIES) {
				break;
			}
			if (!entity.isAlive() || entity.isSpectator()) {
				continue;
			}
			AABB box = entity.getBoundingBox();
			if (box.maxY < playerPos.y - 4 || box.minY > playerPos.y + 6) {
				continue;
			}
			Vec3 vel = entity.getDeltaMovement();
			addOccupant(new Occupant(box, vel.x, vel.z));
			taken++;
		}

		try {
			int subTaken = 0;
			BoundingBox3d query = new BoundingBox3d(search);
			for (SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, query)) {
				if (subTaken >= MAX_SUBLEVELS) {
					break;
				}
				BoundingBox3dc bb = subLevel.boundingBox();
				AABB box = new AABB(bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ());
				if (!box.intersects(search)) {
					continue;
				}
				double vx = 0;
				double vz = 0;
				try {
					Vector3d now = new Vector3d(subLevel.logicalPose().position());
					Vector3d prev = new Vector3d(subLevel.lastPose().position());
					vx = now.x - prev.x;
					vz = now.z - prev.z;
				} catch (Exception ignored) {
				}
				addOccupant(new Occupant(box, vx, vz));
				subTaken++;
			}
		} catch (Throwable ignored) {
			// Sable API unavailable or container not ready.
		}
	}

	void emitWakes(float wakeStrength) {
		if (wakeStrength <= 0.001F) {
			return;
		}
		for (Occupant occupant : occupants) {
			if (occupant.speed < 0.04) {
				continue;
			}
			if (wakes.size() >= MAX_WAKES) {
				break;
			}
			double inv = 1.0 / occupant.speed;
			wakes.add(new Wake(
				occupant.cx,
				occupant.cz,
				occupant.vx * inv,
				occupant.vz * inv,
				wakeStrength * (float) Math.min(occupant.speed * 4.0, 1.5)
			));
		}
		wakes.removeIf(wake -> ++wake.age > WAKE_LIFE);
		if (wakes.size() > MAX_WAKES) {
			wakes.subList(0, wakes.size() - MAX_WAKES).clear();
		}
	}

	double wakeHeight(double x, double z, float wavelength) {
		if (wakes.isEmpty()) {
			return 0;
		}
		double k = (Math.PI * 2.0) / Math.max(0.5, wavelength * 0.65);
		double h = 0;
		for (Wake wake : wakes) {
			double dx = x - wake.x;
			double dz = z - wake.z;
			double dist = Math.sqrt(dx * dx + dz * dz);
			double forward = dx * wake.dirX + dz * wake.dirZ;
			double envelope = Math.exp(-dist / 2.4) * Math.exp(-wake.age / 18.0);
			double bow = forward > 0 ? 1.15 : 0.55;
			h += wake.strength * 0.22 * bow * envelope * Math.sin(k * dist - wake.age * 0.35);
		}
		return h;
	}

	int pushOut(net.minecraft.world.phys.Vec3[] posHolder, net.minecraft.world.phys.Vec3[] velHolder, float cubeSize) {
		if (occupants.isEmpty()) {
			return 0;
		}
		Vec3 pos = posHolder[0];
		Vec3 vel = velHolder[0];
		float half = DebrisWorldQuery.collisionHalf(cubeSize);
		AABB cube = DebrisWorldQuery.cubeBox(pos.x, pos.y, pos.z, cubeSize);
		int ax = Mth.floor(pos.x);
		int az = Mth.floor(pos.z);
		int contacts = 0;
		int checks = 0;
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 1; oz++) {
				ArrayList<Occupant> bin = cells.get(BlockPos.asLong(ax + ox, 0, az + oz));
				if (bin == null) {
					continue;
				}
				for (Occupant occupant : bin) {
					if (++checks > MAX_PAIR_CHECKS) {
						posHolder[0] = pos;
						velHolder[0] = vel;
						return contacts;
					}
					AABB inflated = occupant.box.inflate(half, half * 0.35, half);
					if (!cube.intersects(inflated)) {
						continue;
					}
					contacts++;
					double dx = pos.x - occupant.cx;
					double dz = pos.z - occupant.cz;
					double distSq = dx * dx + dz * dz;
					double minDist = half + Math.max(occupant.box.getXsize(), occupant.box.getZsize()) * 0.15;
					minDist = Math.max(minDist, half + 0.15);
					if (distSq < 1.0e-6) {
						dx = 0.12;
						dz = 0.0;
						distSq = 0.0144;
					}
					double dist = Math.sqrt(distSq);
					if (dist < minDist) {
						double push = (minDist - dist);
						pos = new Vec3(pos.x + dx / dist * push, pos.y, pos.z + dz / dist * push);
						cube = DebrisWorldQuery.cubeBox(pos.x, pos.y, pos.z, cubeSize);
					}
					if (cube.intersects(occupant.box)) {
						double nx = pos.x < occupant.cx ? occupant.box.minX - half - 0.02 : occupant.box.maxX + half + 0.02;
						double nz = pos.z < occupant.cz ? occupant.box.minZ - half - 0.02 : occupant.box.maxZ + half + 0.02;
						double altX = Math.abs(nx - pos.x);
						double altZ = Math.abs(nz - pos.z);
						if (altX < altZ) {
							pos = new Vec3(nx, pos.y, pos.z);
						} else {
							pos = new Vec3(pos.x, pos.y, nz);
						}
						cube = DebrisWorldQuery.cubeBox(pos.x, pos.y, pos.z, cubeSize);
					}
					vel = new Vec3(vel.x + occupant.vx * 0.35, vel.y, vel.z + occupant.vz * 0.35);
				}
			}
		}
		posHolder[0] = pos;
		velHolder[0] = vel;
		return contacts;
	}

	boolean insideAny(double x, double y, double z, float size) {
		AABB cube = DebrisWorldQuery.cubeBox(x, y, z, size);
		int ax = Mth.floor(x);
		int az = Mth.floor(z);
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 1; oz++) {
				ArrayList<Occupant> bin = cells.get(BlockPos.asLong(ax + ox, 0, az + oz));
				if (bin == null) {
					continue;
				}
				for (Occupant occupant : bin) {
					if (cube.intersects(occupant.box)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private void addOccupant(Occupant occupant) {
		occupants.add(occupant);
		int minX = Mth.floor(occupant.box.minX);
		int maxX = Mth.floor(occupant.box.maxX);
		int minZ = Mth.floor(occupant.box.minZ);
		int maxZ = Mth.floor(occupant.box.maxZ);
		int cellsAdded = 0;
		for (int x = minX; x <= maxX && cellsAdded < 32; x++) {
			for (int z = minZ; z <= maxZ && cellsAdded < 32; z++) {
				cells.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> new ArrayList<>()).add(occupant);
				cellsAdded++;
			}
		}
	}
}
