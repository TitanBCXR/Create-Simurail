package com.crystaelix.simurail.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Cheap client-only queries for debris floating: fluid surfaces, AABB sweeps
 * against block collision shapes, and spawn-fit tests. No entity collision.
 */
@OnlyIn(Dist.CLIENT)
final class DebrisWorldQuery {

	static final int SEARCH_UP = 8;
	static final int SEARCH_DOWN = 6;
	private static final float SQRT3 = 1.7320508F;

	private DebrisWorldQuery() {
	}

	/** Max AABB half-extent of a tumbling cube of edge {@code size}. */
	static float collisionHalf(float size) {
		return size * 0.5F * SQRT3;
	}

	/** Raise the cube center so roughly the bottom 40% sits below the surface. */
	static float submergeOffset(float size) {
		return size * 0.10F;
	}

	static float bobAmplitude(float size) {
		return size * 0.06F;
	}

	static AABB cubeBox(double x, double y, double z, float size) {
		float half = collisionHalf(size);
		return new AABB(x - half, y - half, z - half, x + half, y + half, z + half);
	}

	record Surface(BlockPos pos, FluidState fluid, double y) {
	}

	/**
	 * Top fluid surface in this column: climb out of deep fluid, or search down
	 * a few blocks if the start cell is empty.
	 */
	static Surface findTopFluidSurface(Level level, BlockPos start) {
		BlockPos.MutableBlockPos cursor = start.mutable();
		FluidState fluid = level.getFluidState(cursor);

		if (fluid.isEmpty()) {
			boolean found = false;
			for (int i = 0; i < SEARCH_DOWN; i++) {
				cursor.move(0, -1, 0);
				fluid = level.getFluidState(cursor);
				if (!fluid.isEmpty()) {
					found = true;
					break;
				}
			}
			if (!found) {
				return null;
			}
		}

		int maxY = start.getY() + SEARCH_UP;
		while (cursor.getY() < maxY && !level.getFluidState(cursor.above()).isEmpty()) {
			cursor.move(0, 1, 0);
		}

		fluid = level.getFluidState(cursor);
		if (fluid.isEmpty()) {
			return null;
		}
		double surfaceY = cursor.getY() + fluid.getHeight(level, cursor);
		return new Surface(cursor.immutable(), fluid, surfaceY);
	}

	static Surface findTopFluidInColumn(Level level, int x, int z, int fromY) {
		return findTopFluidSurface(level, new BlockPos(x, fromY, z));
	}

	/**
	 * True when the column has a fluid surface within search range (including a
	 * drop). No fluid at all is treated as a wall.
	 */
	static boolean canEnterColumn(Level level, double x, double z, int fromY) {
		return findTopFluidInColumn(level, Mth.floor(x), Mth.floor(z), fromY) != null;
	}

	static boolean isDrop(Surface next, double currentSurfaceY) {
		return next != null && next.y() < currentSurfaceY - 0.5;
	}

	static double collideAxis(Level level, AABB box, Direction.Axis axis, double desired) {
		if (desired == 0.0) {
			return 0.0;
		}
		double ex = axis == Direction.Axis.X ? desired : 0.0;
		double ey = axis == Direction.Axis.Y ? desired : 0.0;
		double ez = axis == Direction.Axis.Z ? desired : 0.0;
		AABB swept = box.expandTowards(ex, ey, ez);

		int minX = Mth.floor(swept.minX);
		int maxX = Mth.floor(swept.maxX);
		int minY = Mth.floor(swept.minY);
		int maxY = Mth.floor(swept.maxY);
		int minZ = Mth.floor(swept.minZ);
		int maxZ = Mth.floor(swept.maxZ);

		double result = desired;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					VoxelShape shape = state.getCollisionShape(level, pos);
					if (shape.isEmpty()) {
						continue;
					}
					result = shape.move(x, y, z).collide(axis, box, result);
					if (result == 0.0) {
						return 0.0;
					}
				}
			}
		}
		return result;
	}

	static boolean intersectsSolid(Level level, AABB box) {
		int minX = Mth.floor(box.minX);
		int maxX = Mth.floor(box.maxX);
		int minY = Mth.floor(box.minY);
		int maxY = Mth.floor(box.maxY);
		int minZ = Mth.floor(box.minZ);
		int maxZ = Mth.floor(box.maxZ);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					pos.set(x, y, z);
					VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
					if (shape.isEmpty()) {
						continue;
					}
					VoxelShape worldShape = shape.move(x, y, z);
					for (AABB part : worldShape.toAabbs()) {
						if (part.intersects(box)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	static boolean cubeFits(Level level, double x, double y, double z, float size) {
		AABB box = cubeBox(x, y, z, size);
		if (intersectsSolid(level, box)) {
			return false;
		}
		Surface surface = findTopFluidSurface(level, BlockPos.containing(x, y, z));
		return surface != null;
	}
}
