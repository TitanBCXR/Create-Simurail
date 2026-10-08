package com.crystaelix.simurail.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;

import com.crystaelix.simurail.config.SimurailConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Client-side renderer for floating fluid debris using baked models.
 * Cubes float on the water surface, drift with the current, and follow waterfalls.
 * Vanilla fluids always render normally - debris overlays on top.
 */
@OnlyIn(Dist.CLIENT)
public class FluidCubeRenderer {

	private static final List<FloatingDebris> debrisPool = new ArrayList<>();
	private static int tickCounter = 0;
	private static int debugLogTimer = 0;
	private static int columnScanIndex = 0;
	
	private static int lastSpawnAttempts = 0;
	private static int lastSurfaceBlocksFound = 0;
	private static int lastDebrisRendered = 0;
	
	private static final float MODEL_NATIVE_EDGE = 4F / 16F;

	private static BakedModel iceCubeModel;
	private static BakedModel packedIceCubeModel;
	private static BakedModel cubeModel;
	private static BakedModel dropletModel;
	private static BakedModel tileModel;
	private static BakedModel iceCubeLavaModel;
	private static BakedModel cubeLavaModel;
	private static BakedModel dropletLavaModel;
	private static BakedModel tileLavaModel;
	private static boolean modelsLoaded = false;
	
	private static boolean readmeWritten = false;

	/**
	 * Called every client tick to simulate and spawn debris.
	 */
	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			debrisPool.clear();
			return;
		}
		
		try {
			int style = SimurailConfig.client().fluidVisualsRenderStyle.get();
			if (style == 0) {
				debrisPool.clear();
				return;
			}
		} catch (Exception e) {
			debrisPool.clear();
			return;
		}

		tickCounter++;
		debugLogTimer++;
		Level level = mc.level;
		
		// Simulate existing debris
		debrisPool.removeIf(debris -> !debris.tick(level));
		
		// Deterministic spawning: scan columns in a budgeted slice
		spawnDebrisDeterministic(mc);
		
		// Debug logging every 5 seconds (100 ticks)
		if (debugLogTimer >= 100) {
			debugLogTimer = 0;
			try {
				if (SimurailConfig.client().fluidVisualsDebugLogging.get()) {
					System.out.println("[Simurail FluidCubeRenderer] Active: " + debrisPool.size() + 
						", SpawnAttempts: " + lastSpawnAttempts + 
						", SurfaceFound: " + lastSurfaceBlocksFound + 
						", LastRendered: " + lastDebrisRendered);
				}
			} catch (Exception e) {
			}
		}
		
		// Write README on first run
		if (!readmeWritten) {
			writeReadme();
			readmeWritten = true;
		}
	}

	/**
	 * Render all floating debris with model-based rendering.
	 */
	public static void render(PoseStack poseStack, Vec3 camPos, float partialTick, 
	                          Frustum frustum, MultiBufferSource.BufferSource bufferSource) {
		try {
			int style = SimurailConfig.client().fluidVisualsRenderStyle.get();
			if (style == 0 || debrisPool.isEmpty()) {
				lastDebrisRendered = 0;
				return;
			}
		} catch (Exception e) {
			lastDebrisRendered = 0;
			return;
		}

		if (!modelsLoaded) {
			loadModels();
		}

		Minecraft mc = Minecraft.getInstance();
		int renderRadius = SimurailConfig.client().fluidVisualsRenderRadius.get();
		double radiusSq = renderRadius * renderRadius;
		
		int rendered = 0;
		for (FloatingDebris debris : debrisPool) {
			Vec3 renderPos = debris.getRenderPos(partialTick);
			double dx = renderPos.x - camPos.x;
			double dy = renderPos.y - camPos.y;
			double dz = renderPos.z - camPos.z;
			
			if (dx * dx + dy * dy + dz * dz > radiusSq) {
				continue;
			}
			
			// Inflated AABB for frustum culling
			AABB box = new AABB(
				renderPos.x - debris.size * 1.5, renderPos.y - debris.size * 1.5, renderPos.z - debris.size * 1.5,
				renderPos.x + debris.size * 1.5, renderPos.y + debris.size * 1.5, renderPos.z + debris.size * 1.5
			);
			
			if (!frustum.isVisible(box)) {
				continue;
			}
			
			debris.render(poseStack, bufferSource, camPos, partialTick, mc.level);
			rendered++;
		}
		
		lastDebrisRendered = rendered;
		bufferSource.endBatch(RenderType.solid());
		bufferSource.endBatch(RenderType.cutout());
		bufferSource.endBatch(RenderType.translucent());
	}

	private static void spawnDebrisDeterministic(Minecraft mc) {
		int renderRadius = SimurailConfig.client().fluidVisualsRenderRadius.get();
		float density = SimurailConfig.client().fluidVisualsDensity.get().floatValue();
		int maxCubes = SimurailConfig.client().fluidVisualsMaxCubes.get();
		
		if (debrisPool.size() >= maxCubes) {
			lastSpawnAttempts = 0;
			lastSurfaceBlocksFound = 0;
			return;
		}
		
		BlockPos playerPos = mc.player.blockPosition();
		Level level = mc.level;
		
		// Budget: scan up to 64 columns per tick
		int columnsPerTick = 64;
		int diameter = renderRadius * 2;
		int totalColumns = diameter * diameter;
		
		int scanned = 0;
		int surfaceFound = 0;
		int spawned = 0;
		
		for (int i = 0; i < columnsPerTick && debrisPool.size() < maxCubes; i++) {
			columnScanIndex = (columnScanIndex + 1) % totalColumns;
			
			int localX = columnScanIndex % diameter;
			int localZ = columnScanIndex / diameter;
			int worldX = playerPos.getX() - renderRadius + localX;
			int worldZ = playerPos.getZ() - renderRadius + localZ;
			
			// Find topmost fluid surface
			BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(worldX, playerPos.getY() + 8, worldZ);
			boolean found = false;
			
			for (int y = playerPos.getY() + 8; y >= playerPos.getY() - 8; y--) {
				mutable.setY(y);
				FluidState fluidState = level.getFluidState(mutable);
				
				if (!fluidState.isEmpty() && level.getFluidState(mutable.above()).isEmpty()) {
					surfaceFound++;
					found = true;
					
					// Spawn based on density probability
					if (level.random.nextFloat() < density) {
						float fluidHeight = fluidState.getHeight(level, mutable);
						// Center sits on the surface so the cube is half-submerged.
						double surfaceY = mutable.getY() + fluidHeight;
						
						double x = mutable.getX() + level.random.nextDouble();
						double z = mutable.getZ() + level.random.nextDouble();
						
						float debrisScale = SimurailConfig.client().fluidVisualsDebrisScale.get().floatValue();
						DebrisMotion motion = DebrisMotion.random(level.random);
						float worldSize = debrisScale * motion.sizeVariation;
						int lifetime = 200 + level.random.nextInt(200);
						
						debrisPool.add(new FloatingDebris(
							new Vec3(x, surfaceY, z),
							fluidState,
							mutable.immutable(),
							worldSize,
							lifetime,
							motion,
							level.random.nextBoolean()
						));
						spawned++;
					}
					break;
				}
			}
			
			scanned++;
		}
		
		lastSpawnAttempts = scanned;
		lastSurfaceBlocksFound = surfaceFound;
	}

	private static void loadModels() {
		try {
			Minecraft mc = Minecraft.getInstance();
			iceCubeModel = mc.getModelManager().getModel(ModelResourceLocation.standalone(
				ResourceLocation.fromNamespaceAndPath("simurail", "fluid_debris/ice_cube")));
			packedIceCubeModel = mc.getModelManager().getModel(ModelResourceLocation.standalone(
				ResourceLocation.fromNamespaceAndPath("simurail", "fluid_debris/packed_ice_cube")));
			cubeModel = mc.getModelManager().getModel(ModelResourceLocation.standalone(
				ResourceLocation.fromNamespaceAndPath("simurail", "fluid_debris/cube")));
			dropletModel = mc.getModelManager().getModel(ModelResourceLocation.standalone(
				ResourceLocation.fromNamespaceAndPath("simurail", "fluid_debris/droplet")));
			tileModel = mc.getModelManager().getModel(ModelResourceLocation.standalone(
				ResourceLocation.fromNamespaceAndPath("simurail", "fluid_debris/tile")));
			iceCubeLavaModel = mc.getModelManager().getModel(ModelResourceLocation.standalone(
				ResourceLocation.fromNamespaceAndPath("simurail", "fluid_debris/ice_cube_lava")));
			cubeLavaModel = mc.getModelManager().getModel(ModelResourceLocation.standalone(
				ResourceLocation.fromNamespaceAndPath("simurail", "fluid_debris/cube_lava")));
			dropletLavaModel = mc.getModelManager().getModel(ModelResourceLocation.standalone(
				ResourceLocation.fromNamespaceAndPath("simurail", "fluid_debris/droplet_lava")));
			tileLavaModel = mc.getModelManager().getModel(ModelResourceLocation.standalone(
				ResourceLocation.fromNamespaceAndPath("simurail", "fluid_debris/tile_lava")));
			modelsLoaded = true;
		} catch (Exception e) {
			System.err.println("[Simurail] Failed to load fluid debris models: " + e.getMessage());
		}
	}

	private static BakedModel getSelectedModel(boolean isLava, boolean packedIce) {
		int style = SimurailConfig.client().fluidVisualsRenderStyle.get();
		if (isLava) {
			return switch (style) {
				case 1 -> iceCubeLavaModel != null ? iceCubeLavaModel : cubeLavaModel;
				case 2 -> dropletLavaModel != null ? dropletLavaModel : cubeLavaModel;
				case 3 -> tileLavaModel != null ? tileLavaModel : cubeLavaModel;
				default -> iceCubeLavaModel != null ? iceCubeLavaModel : cubeLavaModel;
			};
		} else {
			return switch (style) {
				case 1 -> packedIce && packedIceCubeModel != null ? packedIceCubeModel : iceCubeModel;
				case 2 -> dropletModel != null ? dropletModel : iceCubeModel;
				case 3 -> tileModel != null ? tileModel : iceCubeModel;
				default -> iceCubeModel;
			};
		}
	}

	private static void writeReadme() {
		try {
			Path configDir = Paths.get("config", "simurail");
			Files.createDirectories(configDir);
			
			Path readmePath = configDir.resolve("README_fluid_models.txt");
			if (!Files.exists(readmePath)) {
				String readme = """
					Fluid Debris Custom Models
					===========================
					
					You can create custom fluid debris models using Blockbench and override them via resource pack.
					
					## Model Format
					
					Place your models at: assets/simurail/models/fluid_debris/<name>.json
					
					Use standard Blockbench JSON format with texture variable '#fluid':
					
					{
					  "parent": "minecraft:block/block",
					  "textures": {
					    "particle": "#fluid",
					    "all": "#fluid"
					  },
					  "elements": [
					    // Your cube definitions here
					  ]
					}
					
					## OBJ Models (Optional)
					
					You can also use OBJ models via NeoForge's built-in loader:
					
					{
					  "loader": "neoforge:obj",
					  "model": "simurail:models/fluid_debris/custom.obj",
					  "textures": {
					    "fluid": "#fluid"
					  }
					}
					
					Note: Blender FBX export is NOT supported by Minecraft.
					Convert FBX to OBJ before using (File > Export > Wavefront OBJ in Blender).
					
					## Resource Pack Override
					
					1. Create a resource pack with your model at the path above
					2. Set 'debrisModel' in simurail-client.toml to your model location
					3. Reload resources (F3+T)
					
					Built-in models: simurail:fluid_debris/ice_cube, packed_ice_cube, cube, droplet, tile
					Lava: ice_cube_lava (magma), cube_lava, droplet_lava, tile_lava
					World size is set by client config debrisScale (PoseStack), not the JSON element size.
					""";
				
				Files.writeString(readmePath, readme);
			}
		} catch (IOException e) {
			System.err.println("[Simurail] Failed to write README_fluid_models.txt: " + e.getMessage());
		}
	}

	/**
	 * A single floating debris piece.
	 */
	private static class FloatingDebris {
		Vec3 pos;
		Vec3 prevPos;
		Vec3 velocity;
		FluidState fluidState;
		BlockPos originBlock;
		float size;
		int lifetime;
		int age;
		final DebrisMotion motion;
		final boolean packedIce;
		final Quaternionf renderRotation = new Quaternionf();

		FloatingDebris(Vec3 pos, FluidState fluidState, BlockPos originBlock, float size, int lifetime,
				DebrisMotion motion, boolean packedIce) {
			this.pos = pos;
			this.prevPos = pos;
			this.velocity = Vec3.ZERO;
			this.fluidState = fluidState;
			this.originBlock = originBlock;
			this.size = size;
			this.lifetime = lifetime;
			this.age = 0;
			this.motion = motion;
			this.packedIce = packedIce;
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
			
			Vec3 flow = currentFluid.getFlow(level, currentBlock);
			float flowSpeed = (float) flow.length();
			Vec3 targetFlow = flow.scale(0.05);
			
			FluidState below = level.getFluidState(currentBlock.below());
			boolean isFalling = below.isEmpty() || 
				(below.is(FluidTags.WATER) && level.getFluidState(currentBlock.below().above()).isEmpty());
			
			if (isFalling) {
				velocity = velocity.add(0, -0.04, 0);
			} else {
				velocity = velocity.scale(0.9).add(targetFlow.scale(0.1));
				
				float fluidHeight = currentFluid.getHeight(level, currentBlock);
				double targetY = currentBlock.getY() + fluidHeight + motion.bobOffset(age, size);
				double dy = (targetY - pos.y) * 0.1;
				velocity = new Vec3(velocity.x, dy, velocity.z);
			}
			
			pos = pos.add(velocity);
			
			float spinSpeed = SimurailConfig.client().fluidVisualsDebrisSpinSpeed.get().floatValue();
			motion.tick(flowSpeed, spinSpeed);
			
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
			boolean isLava = fluidState.is(FluidTags.LAVA);
			BakedModel model = getSelectedModel(isLava, packedIce);
			if (model == null) return;
			
			Vec3 renderPos = getRenderPos(partialTick);
			
			float x = (float) (renderPos.x - camPos.x);
			float y = (float) (renderPos.y - camPos.y);
			float z = (float) (renderPos.z - camPos.z);
			
			poseStack.pushPose();
			poseStack.translate(x, y, z);
			poseStack.mulPose(motion.interpolated(partialTick, renderRotation));
			float poseScale = size / MODEL_NATIVE_EDGE;
			poseStack.scale(poseScale, poseScale, poseScale);
			poseStack.translate(-0.5F, -0.5F, -0.5F);
			
			int color;
			int light;
			RenderType renderType;
			int style = SimurailConfig.client().fluidVisualsRenderStyle.get();
			
			if (fluidState.is(FluidTags.WATER)) {
				Biome biome = level.getBiome(originBlock).value();
				int waterColor = biome.getWaterColor();
				int wr = (waterColor >> 16) & 0xFF;
				int wg = (waterColor >> 8) & 0xFF;
				int wb = waterColor & 0xFF;
				int r;
				int g;
				int b;
				int a;
				if (style == 1) {
					// Ice: mostly white with a slight biome water tint.
					r = (255 * 3 + wr) / 4;
					g = (255 * 3 + wg) / 4;
					b = (255 * 3 + wb) / 4;
					a = packedIce ? 230 : 200;
				} else {
					r = wr;
					g = wg;
					b = wb;
					a = 217;
				}
				color = (a << 24) | (r << 16) | (g << 8) | b;
				light = LevelRenderer.getLightColor(level, BlockPos.containing(renderPos));
				renderType = RenderType.translucent();
			} else if (fluidState.is(FluidTags.LAVA)) {
				color = 0xFFFFFFFF;
				light = 0xF000F0;
				renderType = style == 1 ? RenderType.solid() : RenderType.cutout();
			} else {
				color = 0xD93232FF;
				light = LevelRenderer.getLightColor(level, BlockPos.containing(renderPos));
				renderType = RenderType.translucent();
			}
			
			VertexConsumer buffer = bufferSource.getBuffer(renderType);
			RandomSource random = RandomSource.create(42);
			
			// Render all quads from the baked model
			for (Direction direction : Direction.values()) {
				List<BakedQuad> quads = model.getQuads(null, direction, random, ModelData.EMPTY, renderType);
				for (BakedQuad quad : quads) {
					buffer.putBulkData(poseStack.last(), quad, 
						(color >> 16 & 0xFF) / 255f,
						(color >> 8 & 0xFF) / 255f,
						(color & 0xFF) / 255f,
						(color >> 24 & 0xFF) / 255f,
						light, 0x00F000F0);
				}
			}
			
			// Render unculled quads
			List<BakedQuad> unculledQuads = model.getQuads(null, null, random, ModelData.EMPTY, renderType);
			for (BakedQuad quad : unculledQuads) {
				buffer.putBulkData(poseStack.last(), quad, 
					(color >> 16 & 0xFF) / 255f,
					(color >> 8 & 0xFF) / 255f,
					(color & 0xFF) / 255f,
					(color >> 24 & 0xFF) / 255f,
					light, 0x00F000F0);
			}
			
			poseStack.popPose();
		}
	}
}
