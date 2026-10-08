package com.crystaelix.simurail.client;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.crystaelix.simurail.client.fluid.FluidMesh;
import com.crystaelix.simurail.config.SimurailConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side renderer for flowing fluid cubes.
 * Renders small animated cubes that flow along fluid currents.
 * This is purely visual and creates no server-side physics bodies.
 */
@OnlyIn(Dist.CLIENT)
public class FluidCubeRenderer {

	private static final List<FluidCube> activeCubes = new ArrayList<>();
	private static int tickCounter = 0;
	
	// Cached meshes for each render style
	private static FluidMesh cubeMesh;
	private static FluidMesh dropletMesh;
	private static FluidMesh tileMesh;
	
	static {
		cubeMesh = FluidMesh.createCube();
		dropletMesh = FluidMesh.createDroplet();
		tileMesh = FluidMesh.createTile();
	}

	/**
	 * Called every client tick to update and spawn cubes.
	 */
	public static void tick(Minecraft mc) {
		// Check if custom rendering is enabled (0=VANILLA means no custom rendering)
		try {
			int style = SimurailConfig.client().fluidVisualsRenderStyle.get();
			if (style == 0) { // VANILLA
				activeCubes.clear();
				return;
			}
		} catch (Exception e) {
			activeCubes.clear();
			return;
		}

		tickCounter++;

		// Update existing cubes
		activeCubes.removeIf(cube -> !cube.tick(mc.level));

		// Spawn new cubes periodically
		if (tickCounter % 2 == 0 && mc.player != null && mc.level != null) {
			spawnCubes(mc);
		}
	}

	/**
	 * Render all active fluid cubes.
	 */
	public static void render(PoseStack poseStack, Camera camera, float partialTick) {
		// Check if custom rendering is enabled (0=VANILLA means no custom rendering)
		try {
			int style = SimurailConfig.client().fluidVisualsRenderStyle.get();
			if (style == 0 || activeCubes.isEmpty()) { // VANILLA or no cubes
				return;
			}
		} catch (Exception e) {
			return;
		}

		Vec3 camPos = camera.getPosition();
		
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		RenderSystem.depthMask(true);
		
		Tesselator tesselator = Tesselator.getInstance();
		Matrix4f matrix = poseStack.last().pose();
		
		BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		
		for (FluidCube cube : activeCubes) {
			cube.render(buffer, matrix, camPos, partialTick);
		}
		
		// Upload and draw the mesh
		var builtBuffer = buffer.buildOrThrow();
		com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(builtBuffer);
		
		RenderSystem.disableBlend();
	}

	/**
	 * Spawn new cubes near the player.
	 */
	private static void spawnCubes(Minecraft mc) {
		// Get current mesh and its triangle count
		FluidMesh currentMesh = getSelectedMesh();
		int meshTriangles = currentMesh.getTriangleCount();
		
		// Enforce triangle budget: reduce max cubes to fit within clientMaxFluidTriangles
		int configMaxCubes = SimurailConfig.client().fluidVisualsMaxCubes.get();
		int maxTriangles = SimurailConfig.client().fluidVisualsMaxTriangles.get();
		int effectiveMaxCubes = Math.min(configMaxCubes, maxTriangles / Math.max(1, meshTriangles));
		
		if (activeCubes.size() >= effectiveMaxCubes) {
			// Remove farthest cubes
			Vec3 playerPos = mc.player.position();
			activeCubes.sort((a, b) -> Double.compare(
				b.pos.distanceToSqr(playerPos.x, playerPos.y, playerPos.z),
				a.pos.distanceToSqr(playerPos.x, playerPos.y, playerPos.z)
			));
			while (activeCubes.size() >= effectiveMaxCubes) {
				activeCubes.remove(activeCubes.size() - 1);
			}
		}

		int renderRadius = SimurailConfig.client().fluidVisualsRenderRadius.get();
		int cubesPerBlock = SimurailConfig.client().fluidVisualsCubesPerBlock.get();
		int subdivision = cubesPerBlock == 1 ? 1 : (cubesPerBlock == 8 ? 2 : 3);

		BlockPos playerPos = mc.player.blockPosition();
		Level level = mc.level;

		// Sample a few random blocks near the player
		for (int i = 0; i < 8; i++) {
			int dx = level.random.nextInt(renderRadius * 2) - renderRadius;
			int dy = level.random.nextInt(8) - 4;
			int dz = level.random.nextInt(renderRadius * 2) - renderRadius;
			
			BlockPos pos = playerPos.offset(dx, dy, dz);
			FluidState fluidState = level.getFluidState(pos);
			
			if (fluidState.isEmpty()) {
				continue;
			}

			// Only spawn cubes for exposed fluid blocks
			if (!isFluidExposed(level, pos)) {
				continue;
			}

			// Spawn a cube (or multiple for subdivision)
			Vec3 flow = fluidState.getFlow(level, pos);
			int color = getFluidColor(level, pos, fluidState);
			
		// Spawn one cube per subdivision cell
		for (int sx = 0; sx < subdivision; sx++) {
			for (int sy = 0; sy < subdivision; sy++) {
				for (int sz = 0; sz < subdivision; sz++) {
					if (activeCubes.size() >= effectiveMaxCubes) {
						return;
					}
						
						double cubeSize = 1.0 / subdivision;
						double offsetX = pos.getX() + sx * cubeSize + cubeSize * 0.5;
						double offsetY = pos.getY() + sy * cubeSize + cubeSize * 0.5;
						double offsetZ = pos.getZ() + sz * cubeSize + cubeSize * 0.5;
						
						activeCubes.add(new FluidCube(
							new Vec3(offsetX, offsetY, offsetZ),
							flow,
							color,
							cubeSize * 0.8, // Slightly smaller for visual gap
							level.random.nextFloat() * 20f // Random phase for animation
						));
					}
				}
			}
		}
	}

	/**
	 * Get the currently selected fluid mesh based on config.
	 */
	private static FluidMesh getSelectedMesh() {
		int style = SimurailConfig.client().fluidVisualsRenderStyle.get();
		return switch (style) {
			case 1 -> cubeMesh;      // CUBE
			case 2 -> dropletMesh;   // DROPLET
			case 3 -> tileMesh;      // TILE
			default -> cubeMesh;     // Fallback to cube
		};
	}
	
	/**
	 * Check if a fluid block has any exposed faces (not fully surrounded).
	 */
	private static boolean isFluidExposed(Level level, BlockPos pos) {
		// Check if any neighbor is air or non-full block
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) continue;
					
					BlockPos neighbor = pos.offset(dx, dy, dz);
					if (level.getFluidState(neighbor).isEmpty() || 
						!level.getBlockState(neighbor).isSolidRender(level, neighbor)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Get the color of a fluid at a position (biome-tinted for water, emissive for lava).
	 */
	private static int getFluidColor(Level level, BlockPos pos, FluidState fluidState) {
		if (fluidState.is(FluidTags.WATER)) {
			// Get biome water color
			Biome biome = level.getBiome(pos).value();
			int waterColor = biome.getWaterColor();
			int r = (waterColor >> 16) & 0xFF;
			int g = (waterColor >> 8) & 0xFF;
			int b = waterColor & 0xFF;
			return (180 << 24) | (r << 16) | (g << 8) | b; // 70% alpha
		} else if (fluidState.is(FluidTags.LAVA)) {
			// Lava: bright orange-red, full alpha
			return (255 << 24) | (255 << 16) | (100 << 8) | 20;
		}
		
		// Default: translucent blue
		return (180 << 24) | (50 << 16) | (50 << 8) | 255;
	}

	/**
	 * A single flowing cube particle.
	 */
	private static class FluidCube {
		Vec3 pos;
		Vec3 flow;
		int color;
		double size;
		float age;

		FluidCube(Vec3 pos, Vec3 flow, int color, double size, float age) {
			this.pos = pos;
			this.flow = flow;
			this.color = color;
			this.size = size;
			this.age = age;
		}

		boolean tick(Level level) {
			age++;

			// Animate along flow direction with wrapping
			if (flow.lengthSqr() > 0.001) {
				double speed = 0.02;
				Vec3 movement = flow.scale(speed);
				pos = pos.add(movement);

				// Wrap position within block bounds
				BlockPos blockPos = BlockPos.containing(pos);
				double localX = pos.x - blockPos.getX();
				double localY = pos.y - blockPos.getY();
				double localZ = pos.z - blockPos.getZ();

				if (localX < 0 || localX > 1) {
					pos = new Vec3(blockPos.getX() + 0.5, pos.y, pos.z);
				}
				if (localY < 0 || localY > 1) {
					pos = new Vec3(pos.x, blockPos.getY() + 0.5, pos.z);
				}
				if (localZ < 0 || localZ > 1) {
					pos = new Vec3(pos.x, pos.y, blockPos.getZ() + 0.5);
				}
			}

			// Check if still in fluid
			if (level != null) {
				FluidState fluidState = level.getFluidState(BlockPos.containing(pos));
				if (fluidState.isEmpty()) {
					return false; // Remove cube
				}
			}

			// Lifetime limit
			return age < 300; // 15 seconds at 20 tps
		}

		void render(BufferBuilder buffer, Matrix4f matrix, Vec3 camPos, float partialTick) {
			float x = (float)(pos.x - camPos.x);
			float y = (float)(pos.y - camPos.y);
			float z = (float)(pos.z - camPos.z);
			
			// Use selected mesh to render
			FluidMesh mesh = getSelectedMesh();
			mesh.render(buffer, matrix, x, y, z, (float)size, color);
		}
	}
}
