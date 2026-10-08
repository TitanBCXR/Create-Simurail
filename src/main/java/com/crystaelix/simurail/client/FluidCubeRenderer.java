package com.crystaelix.simurail.client;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.crystaelix.simurail.client.fluid.FluidMesh;
import com.crystaelix.simurail.config.SimurailConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * Client-side renderer for floating fluid cubes.
 * Cubes float on the water surface, drift with the current, and follow waterfalls.
 * Vanilla fluids always render normally - cubes overlay on top.
 */
@OnlyIn(Dist.CLIENT)
public class FluidCubeRenderer {

	private static final List<FloatingCube> cubePool = new ArrayList<>();
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
	 * Called every client tick to simulate and spawn cubes.
	 */
	public static void tick(Minecraft mc) {
		try {
			int style = SimurailConfig.client().fluidVisualsRenderStyle.get();
			if (style == 0 || mc.level == null || mc.player == null) {
				cubePool.clear();
				return;
			}
		} catch (Exception e) {
			cubePool.clear();
			return;
		}

		tickCounter++;
		Level level = mc.level;
		
		// Simulate existing cubes
		cubePool.removeIf(cube -> !cube.tick(level));
		
		// Spawn new cubes on surface blocks
		if (tickCounter % 2 == 0) {
			spawnCubesOnSurface(mc);
		}
	}

	/**
	 * Render all floating cubes with frustum culling and batching.
	 */
	public static void render(PoseStack poseStack, Camera camera, float partialTick, Frustum frustum) {
		try {
			int style = SimurailConfig.client().fluidVisualsRenderStyle.get();
			if (style == 0 || cubePool.isEmpty()) {
				return;
			}
		} catch (Exception e) {
			return;
		}

		Vec3 camPos = camera.getPosition();
		Minecraft mc = Minecraft.getInstance();
		MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
		
		int renderRadius = SimurailConfig.client().fluidVisualsRenderRadius.get();
		double radiusSq = renderRadius * renderRadius;
		
		for (FloatingCube cube : cubePool) {
			Vec3 renderPos = cube.getRenderPos(partialTick);
			double dx = renderPos.x - camPos.x;
			double dy = renderPos.y - camPos.y;
			double dz = renderPos.z - camPos.z;
			
			if (dx * dx + dy * dy + dz * dz > radiusSq) {
				continue;
			}
			
			AABB box = new AABB(
				renderPos.x - cube.size, renderPos.y - cube.size, renderPos.z - cube.size,
				renderPos.x + cube.size, renderPos.y + cube.size, renderPos.z + cube.size
			);
			
			if (!frustum.isVisible(box)) {
				continue;
			}
			
			cube.render(poseStack, bufferSource, camPos, partialTick, mc.level);
		}
		
		bufferSource.endBatch();
	}

	private static void spawnCubesOnSurface(Minecraft mc) {
		FluidMesh currentMesh = getSelectedMesh();
		int meshTriangles = currentMesh.getTriangleCount();
		
		int configMaxCubes = SimurailConfig.client().fluidVisualsMaxCubes.get();
		int maxTriangles = SimurailConfig.client().fluidVisualsMaxTriangles.get();
		int effectiveMaxCubes = Math.min(configMaxCubes, maxTriangles / Math.max(1, meshTriangles));
		
		if (cubePool.size() >= effectiveMaxCubes) {
			return;
		}
		
		int renderRadius = SimurailConfig.client().fluidVisualsRenderRadius.get();
		float density = SimurailConfig.client().fluidVisualsDensity.get().floatValue();
		BlockPos playerPos = mc.player.blockPosition();
		Level level = mc.level;
		
		// Sample random surface blocks
		int attempts = (int) (density * 16);
		for (int i = 0; i < attempts && cubePool.size() < effectiveMaxCubes; i++) {
			int dx = level.random.nextInt(renderRadius * 2) - renderRadius;
			int dy = level.random.nextInt(16) - 8;
			int dz = level.random.nextInt(renderRadius * 2) - renderRadius;
			
			BlockPos pos = playerPos.offset(dx, dy, dz);
			FluidState fluidState = level.getFluidState(pos);
			
			if (fluidState.isEmpty()) {
				continue;
			}
			
			// Only spawn on surface (air above)
			if (!level.getFluidState(pos.above()).isEmpty()) {
				continue;
			}
			
			float fluidHeight = fluidState.getHeight(level, pos);
			double surfaceY = pos.getY() + fluidHeight;
			
			// Random position on surface
			double x = pos.getX() + level.random.nextDouble();
			double z = pos.getZ() + level.random.nextDouble();
			
			// Random size (0.15 to 0.3 blocks)
			float size = 0.15f + level.random.nextFloat() * 0.15f;
			
			// Random lifetime (10-20 seconds)
			int lifetime = 200 + level.random.nextInt(200);
			
			cubePool.add(new FloatingCube(
				new Vec3(x, surfaceY, z),
				fluidState,
				pos,
				size,
				lifetime
			));
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

	/**
	 * A single floating cube on the water surface.
	 */
	private static class FloatingCube {
		Vec3 pos;
		Vec3 prevPos;
		Vec3 velocity;
		FluidState fluidState;
		BlockPos originBlock;
		float size;
		int lifetime;
		int age;
		Quaternionf rotation;
		float rotationSpeed;
		float bobPhase;

		FloatingCube(Vec3 pos, FluidState fluidState, BlockPos originBlock, float size, int lifetime) {
			this.pos = pos;
			this.prevPos = pos;
			this.velocity = Vec3.ZERO;
			this.fluidState = fluidState;
			this.originBlock = originBlock;
			this.size = size;
			this.lifetime = lifetime;
			this.age = 0;
			this.rotation = new Quaternionf();
			this.rotationSpeed = (float) ((Math.random() - 0.5) * 0.02);
			this.bobPhase = (float) (Math.random() * Math.PI * 2);
		}

		boolean tick(Level level) {
			age++;
			prevPos = pos;
			
			if (age >= lifetime) {
				return false;
			}
			
			BlockPos currentBlock = BlockPos.containing(pos);
			FluidState currentFluid = level.getFluidState(currentBlock);
			
			if (currentFluid.isEmpty()) {
				return false;
			}
			
			// Get target flow velocity
			Vec3 targetFlow = currentFluid.getFlow(level, currentBlock).scale(0.05);
			
			// Check if falling (fluid below is falling or no surface)
			FluidState below = level.getFluidState(currentBlock.below());
			boolean isFalling = below.isEmpty() || 
				(below.is(FluidTags.WATER) && level.getFluidState(currentBlock.below().above()).isEmpty());
			
			if (isFalling) {
				// Fall with gravity
				velocity = velocity.add(0, -0.04, 0);
			} else {
				// Push toward flow with damping
				velocity = velocity.scale(0.9).add(targetFlow.scale(0.1));
				
				// Target surface height
				float fluidHeight = currentFluid.getHeight(level, currentBlock);
				double targetY = currentBlock.getY() + fluidHeight;
				
				// Bob gently
				double bob = Math.sin((age + bobPhase) * 0.1) * 0.02;
				targetY += bob;
				
				// Smooth Y toward surface
				double dy = (targetY - pos.y) * 0.1;
				velocity = new Vec3(velocity.x, dy, velocity.z);
			}
			
			// Apply velocity
			pos = pos.add(velocity);
			
			// Rotate slowly
			rotation.rotateY(rotationSpeed);
			
			return true;
		}

		Vec3 getRenderPos(float partialTick) {
			return new Vec3(
				prevPos.x + (pos.x - prevPos.x) * partialTick,
				prevPos.y + (pos.y - prevPos.y) * partialTick,
				prevPos.z + (pos.z - prevPos.z) * partialTick
			);
		}

		void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 camPos, 
		            float partialTick, Level level) {
			Vec3 renderPos = getRenderPos(partialTick);
			float x = (float) (renderPos.x - camPos.x);
			float y = (float) (renderPos.y - camPos.y);
			float z = (float) (renderPos.z - camPos.z);
			
			poseStack.pushPose();
			poseStack.translate(x, y, z);
			poseStack.mulPose(rotation);
			
			IClientFluidTypeExtensions fluidExtensions = IClientFluidTypeExtensions.of(fluidState);
			ResourceLocation textureLocation = fluidExtensions.getStillTexture();
			
			TextureAtlasSprite sprite = Minecraft.getInstance()
				.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
				.apply(textureLocation);
			
			int color;
			int light;
			RenderType renderType;
			
			if (fluidState.is(FluidTags.WATER)) {
				Biome biome = level.getBiome(originBlock).value();
				int waterColor = biome.getWaterColor();
				int r = (waterColor >> 16) & 0xFF;
				int g = (waterColor >> 8) & 0xFF;
				int b = waterColor & 0xFF;
				color = (217 << 24) | (r << 16) | (g << 8) | b; // 85% alpha
				light = LevelRenderer.getLightColor(level, BlockPos.containing(renderPos));
				renderType = RenderType.translucent();
			} else if (fluidState.is(FluidTags.LAVA)) {
				color = (255 << 24) | (255 << 16) | (100 << 8) | 20;
				light = 0xF000F0;
				renderType = RenderType.solid();
			} else {
				color = (217 << 24) | (50 << 16) | (50 << 8) | 255;
				light = LevelRenderer.getLightColor(level, BlockPos.containing(renderPos));
				renderType = RenderType.translucent();
			}
			
			VertexConsumer buffer = bufferSource.getBuffer(renderType);
			FluidMesh mesh = getSelectedMesh();
			Matrix4f matrix = poseStack.last().pose();
			mesh.renderTextured(buffer, matrix, 0, 0, 0, size, sprite, color, light, 0, 0);
			
			poseStack.popPose();
		}
	}
}
