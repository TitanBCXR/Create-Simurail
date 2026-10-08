package com.crystaelix.simurail.client;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.crystaelix.simurail.client.fluid.FluidMesh;
import com.crystaelix.simurail.config.SimurailConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * Client-side renderer for flowing fluid cubes.
 * Renders small animated cubes with real fluid textures that flow along fluid currents.
 * This is purely visual and creates no server-side physics bodies.
 */
@OnlyIn(Dist.CLIENT)
public class FluidCubeRenderer {

	private static final List<FluidCube> activeCubes = new ArrayList<>();
	private static int tickCounter = 0;
	
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
		try {
			int style = SimurailConfig.client().fluidVisualsRenderStyle.get();
			if (style == 0) {
				activeCubes.clear();
				return;
			}
		} catch (Exception e) {
			activeCubes.clear();
			return;
		}

		tickCounter++;
		activeCubes.removeIf(cube -> !cube.tick(mc.level));

		if (tickCounter % 2 == 0 && mc.player != null && mc.level != null) {
			spawnCubes(mc);
		}
	}

	/**
	 * Render all active fluid cubes with textures.
	 */
	public static void render(PoseStack poseStack, Camera camera, float partialTick) {
		try {
			int style = SimurailConfig.client().fluidVisualsRenderStyle.get();
			if (style == 0 || activeCubes.isEmpty()) {
				return;
			}
		} catch (Exception e) {
			return;
		}

		Vec3 camPos = camera.getPosition();
		Matrix4f matrix = poseStack.last().pose();
		Minecraft mc = Minecraft.getInstance();
		MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
		
		for (FluidCube cube : activeCubes) {
			cube.render(bufferSource, matrix, camPos, partialTick, mc.level);
		}
		
		bufferSource.endBatch();
	}

	private static void spawnCubes(Minecraft mc) {
		FluidMesh currentMesh = getSelectedMesh();
		int meshTriangles = currentMesh.getTriangleCount();
		
		int configMaxCubes = SimurailConfig.client().fluidVisualsMaxCubes.get();
		int maxTriangles = SimurailConfig.client().fluidVisualsMaxTriangles.get();
		int effectiveMaxCubes = Math.min(configMaxCubes, maxTriangles / Math.max(1, meshTriangles));
		
		if (activeCubes.size() >= effectiveMaxCubes) {
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

		for (int i = 0; i < 8; i++) {
			int dx = level.random.nextInt(renderRadius * 2) - renderRadius;
			int dy = level.random.nextInt(8) - 4;
			int dz = level.random.nextInt(renderRadius * 2) - renderRadius;
			
			BlockPos pos = playerPos.offset(dx, dy, dz);
			FluidState fluidState = level.getFluidState(pos);
			
			if (fluidState.isEmpty() || !isFluidExposed(level, pos)) {
				continue;
			}

			Vec3 flow = fluidState.getFlow(level, pos);
			
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
						
						float uvOffsetX = sx * (float)cubeSize;
						float uvOffsetZ = sz * (float)cubeSize;
						
						activeCubes.add(new FluidCube(
							new Vec3(offsetX, offsetY, offsetZ),
							flow,
							fluidState,
							pos,
							cubeSize * 0.8,
							level.random.nextFloat() * 20f,
							uvOffsetX,
							uvOffsetZ
						));
					}
				}
			}
		}
	}

	private static FluidMesh getSelectedMesh() {
		int style = SimurailConfig.client().fluidVisualsRenderStyle.get();
		return switch (style) {
			case 1 -> cubeMesh;
			case 2 -> dropletMesh;
			case 3 -> tileMesh;
			default -> cubeMesh;
		};
	}
	
	private static boolean isFluidExposed(Level level, BlockPos pos) {
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

	private static class FluidCube {
		Vec3 pos;
		Vec3 originalPos;
		Vec3 flow;
		FluidState fluidState;
		BlockPos blockPos;
		double size;
		float age;
		float bobPhase;
		float uvOffsetX;
		float uvOffsetZ;

		FluidCube(Vec3 pos, Vec3 flow, FluidState fluidState, BlockPos blockPos, 
		          double size, float bobPhase, float uvOffsetX, float uvOffsetZ) {
			this.pos = pos;
			this.originalPos = pos;
			this.flow = flow;
			this.fluidState = fluidState;
			this.blockPos = blockPos;
			this.size = size;
			this.age = 0;
			this.bobPhase = bobPhase;
			this.uvOffsetX = uvOffsetX;
			this.uvOffsetZ = uvOffsetZ;
		}

		boolean tick(Level level) {
			age++;

			if (flow.lengthSqr() > 0.001) {
				double speed = 0.02 * Math.min(1.0, flow.length());
				pos = pos.add(flow.scale(speed));
				
				BlockPos currentBlock = BlockPos.containing(pos);
				double localX = pos.x - currentBlock.getX();
				double localY = pos.y - currentBlock.getY();
				double localZ = pos.z - currentBlock.getZ();
				
				if (localX < 0 || localX > 1) pos = new Vec3(currentBlock.getX() + 0.5, pos.y, pos.z);
				if (localY < 0 || localY > 1) pos = new Vec3(pos.x, currentBlock.getY() + 0.5, pos.z);
				if (localZ < 0 || localZ > 1) pos = new Vec3(pos.x, pos.y, currentBlock.getZ() + 0.5);
			}

			if (level != null) {
				FluidState currentFluid = level.getFluidState(BlockPos.containing(pos));
				if (currentFluid.isEmpty()) {
					return false;
				}
			}

			return age < 300;
		}

		void render(MultiBufferSource bufferSource, Matrix4f matrix, Vec3 camPos, 
		            float partialTick, Level level) {
			float x = (float)(pos.x - camPos.x);
			float y = (float)(pos.y - camPos.y);
			float z = (float)(pos.z - camPos.z);
			
			float bob = 0;
			if (flow.lengthSqr() < 0.001 && fluidState.is(FluidTags.WATER)) {
				bob = (float) (Math.sin((age + partialTick + bobPhase) * 0.1) * 0.02);
				y += bob;
			}
			
			IClientFluidTypeExtensions fluidExtensions = IClientFluidTypeExtensions.of(fluidState);
			ResourceLocation textureLocation = flow.lengthSqr() > 0.001 ? 
				fluidExtensions.getFlowingTexture() : fluidExtensions.getStillTexture();
			
			TextureAtlasSprite sprite = Minecraft.getInstance()
				.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
				.apply(textureLocation);
			
			int color;
			int light;
			RenderType renderType;
			
			if (fluidState.is(FluidTags.WATER)) {
				Biome biome = level.getBiome(blockPos).value();
				int waterColor = biome.getWaterColor();
				int r = (waterColor >> 16) & 0xFF;
				int g = (waterColor >> 8) & 0xFF;
				int b = waterColor & 0xFF;
				color = (180 << 24) | (r << 16) | (g << 8) | b;
				light = LevelRenderer.getLightColor(level, blockPos);
				renderType = RenderType.translucent();
			} else if (fluidState.is(FluidTags.LAVA)) {
				color = (255 << 24) | (255 << 16) | (100 << 8) | 20;
				light = 0xF000F0;
				renderType = RenderType.solid();
			} else {
				color = (180 << 24) | (50 << 16) | (50 << 8) | 255;
				light = LevelRenderer.getLightColor(level, blockPos);
				renderType = RenderType.translucent();
			}
			
			VertexConsumer buffer = bufferSource.getBuffer(renderType);
			FluidMesh mesh = getSelectedMesh();
			mesh.renderTextured(buffer, matrix, x, y, z, (float)size, sprite, color, light, uvOffsetX, uvOffsetZ);
		}
	}
}
