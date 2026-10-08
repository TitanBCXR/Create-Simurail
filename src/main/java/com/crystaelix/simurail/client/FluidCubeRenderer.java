package com.crystaelix.simurail.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Quaternionf;

import com.crystaelix.simurail.config.SimurailConfig;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

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
	private static int collisionsThisSecond = 0;
	private static int despawnsThisSecond = 0;
	private static int entityContactsThisSecond = 0;
	private static int collisionLogTimer = 0;
	
	private static final DebrisOccupants occupants = new DebrisOccupants();
	private static Vec3 playerPosThisTick = Vec3.ZERO;
	private static final Set<Long> occupiedSlots = new HashSet<>();
	private static final Quaternionf WAVE_ROLL = new Quaternionf();
	
	private static final float WALL_BOUNCE = 0.35F;
	private static final float GRAVITY = 0.04F;
	private static final float TERMINAL_VELOCITY = 0.4F;
	private static final int SEPARATION_CHECK_CAP = 384;
	
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

	public static void invalidateModels() {
		modelsLoaded = false;
	}

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
		collisionLogTimer++;
		Level level = mc.level;
		playerPosThisTick = mc.player.position();
		
		boolean entityInteraction = SimurailConfig.client().fluidVisualsEntityInteraction.get();
		int renderRadius = SimurailConfig.client().fluidVisualsRenderRadius.get();
		if (entityInteraction) {
			occupants.gather(level, playerPosThisTick, renderRadius);
			occupants.emitWakes(SimurailConfig.client().fluidVisualsWakeStrength.get().floatValue());
		} else {
			occupants.occupants.clear();
			occupants.cells.clear();
			occupants.wakes.clear();
		}
		
		debrisPool.removeIf(debris -> {
			if (debris.tick(level)) {
				return false;
			}
			if (debris.tryWrapUpstream(level, renderRadius)) {
				return false;
			}
			despawnsThisSecond++;
			return true;
		});
		
		separateDebris();
		spawnDebrisDeterministic(mc);
		
		if (collisionLogTimer >= 20) {
			try {
				if (SimurailConfig.client().fluidVisualsDebugLogging.get()) {
					System.out.println("[Simurail FluidCubeRenderer] collisions=" + collisionsThisSecond
						+ "/s despawns=" + despawnsThisSecond + "/s entityContacts=" + entityContactsThisSecond
						+ "/s active=" + debrisPool.size());
				}
			} catch (Exception e) {
			}
			collisionsThisSecond = 0;
			despawnsThisSecond = 0;
			entityContactsThisSecond = 0;
			collisionLogTimer = 0;
		}
		
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
		
		if (!readmeWritten) {
			FluidDebrisCatalog.ensureFolderAndReadme();
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
		
		occupiedSlots.clear();
		for (FloatingDebris debris : debrisPool) {
			occupiedSlots.add(debris.slotKey());
		}
		
		if (debrisPool.size() >= maxCubes) {
			lastSpawnAttempts = 0;
			lastSurfaceBlocksFound = 0;
			return;
		}
		
		BlockPos playerPos = mc.player.blockPosition();
		Level level = mc.level;
		
		int columnsPerTick = 64;
		int diameter = renderRadius * 2;
		int totalColumns = Math.max(1, diameter * diameter);
		
		int scanned = 0;
		int surfaceFound = 0;
		
		for (int i = 0; i < columnsPerTick && debrisPool.size() < maxCubes; i++) {
			columnScanIndex = (columnScanIndex + 1) % totalColumns;
			
			int localX = columnScanIndex % diameter;
			int localZ = columnScanIndex / diameter;
			int worldX = playerPos.getX() - renderRadius + localX;
			int worldZ = playerPos.getZ() - renderRadius + localZ;
			
			BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(worldX, playerPos.getY() + 8, worldZ);
			
			for (int y = playerPos.getY() + 8; y >= playerPos.getY() - 8; y--) {
				mutable.setY(y);
				FluidState fluidState = level.getFluidState(mutable);
				
				if (!fluidState.isEmpty() && level.getFluidState(mutable.above()).isEmpty()) {
					surfaceFound++;
					
					int count = DebrisWaveField.cubesForDensity(density, level.random);
					float debrisScale = SimurailConfig.client().fluidVisualsDebrisScale.get().floatValue();
					for (int slot = 0; slot < count && debrisPool.size() < maxCubes; slot++) {
						long key = BlockPos.asLong(worldX, slot, worldZ);
						if (occupiedSlots.contains(key)) {
							continue;
						}
						DebrisMotion motion = DebrisMotion.random(level.random);
						float worldSize = debrisScale * motion.sizeVariation;
						double[] offset = DebrisWaveField.slotOffsets(slot, Math.max(1, count));
						double jitterX = (level.random.nextDouble() - 0.5) * 0.18;
						double jitterZ = (level.random.nextDouble() - 0.5) * 0.18;
						double x = mutable.getX() + offset[0] + jitterX;
						double z = mutable.getZ() + offset[1] + jitterZ;
						double surfaceY = mutable.getY() + fluidState.getHeight(level, mutable);
						double spawnY = surfaceY + DebrisWorldQuery.submergeOffset(worldSize);
						
						if (!DebrisWorldQuery.cubeFits(level, x, spawnY, z, worldSize)) {
							continue;
						}
						if (SimurailConfig.client().fluidVisualsEntityInteraction.get()
								&& occupants.insideAny(x, spawnY, z, worldSize)) {
							continue;
						}
						
						int lifetime = 400 + level.random.nextInt(400);
						debrisPool.add(new FloatingDebris(
							new Vec3(x, spawnY, z),
							fluidState,
							mutable.immutable(),
							worldSize,
							lifetime,
							motion,
							level.random.nextBoolean(),
							slot,
							worldX,
							worldZ
						));
						occupiedSlots.add(key);
					}
					break;
				}
			}
			
			scanned++;
		}
		
		lastSpawnAttempts = scanned;
		lastSurfaceBlocksFound = surfaceFound;
	}

	private static void separateDebris() {
		int n = debrisPool.size();
		if (n < 2) {
			return;
		}
		Map<Long, ArrayList<Integer>> cells = new HashMap<>();
		for (int i = 0; i < n; i++) {
			Vec3 p = debrisPool.get(i).pos;
			long key = BlockPos.asLong(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z));
			cells.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
		}
		int checks = 0;
		for (int i = 0; i < n && checks < SEPARATION_CHECK_CAP; i++) {
			FloatingDebris a = debrisPool.get(i);
			int ax = Mth.floor(a.pos.x);
			int ay = Mth.floor(a.pos.y);
			int az = Mth.floor(a.pos.z);
			float ha = DebrisWorldQuery.collisionHalf(a.size);
			for (int ox = -1; ox <= 1 && checks < SEPARATION_CHECK_CAP; ox++) {
				for (int oy = -1; oy <= 1 && checks < SEPARATION_CHECK_CAP; oy++) {
					for (int oz = -1; oz <= 1 && checks < SEPARATION_CHECK_CAP; oz++) {
						ArrayList<Integer> bin = cells.get(BlockPos.asLong(ax + ox, ay + oy, az + oz));
						if (bin == null) {
							continue;
						}
						for (int j : bin) {
							if (j <= i) {
								continue;
							}
							checks++;
							if (checks > SEPARATION_CHECK_CAP) {
								return;
							}
							FloatingDebris b = debrisPool.get(j);
							double dx = b.pos.x - a.pos.x;
							double dz = b.pos.z - a.pos.z;
							double distSq = dx * dx + dz * dz;
							float minDist = ha + DebrisWorldQuery.collisionHalf(b.size);
							if (distSq >= (double) minDist * minDist) {
								continue;
							}
							double dist = distSq < 1.0e-8 ? 0.01 : Math.sqrt(distSq);
							if (distSq < 1.0e-8) {
								dx = 0.01;
								dz = 0.0;
							}
							double push = (minDist - dist) * 0.25;
							double nx = dx / dist;
							double nz = dz / dist;
							a.pos = a.pos.add(-nx * push, 0, -nz * push);
							b.pos = b.pos.add(nx * push, 0, nz * push);
						}
					}
				}
			}
		}
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
		String id;
		try {
			id = SimurailConfig.client().fluidVisualsDebrisModel.get();
		} catch (Exception e) {
			id = FluidDebrisModels.WATER_CUBE;
		}
		BakedModel resolved = FluidDebrisModels.resolve(Minecraft.getInstance(), id, isLava, packedIce);
		if (resolved != null) {
			return resolved;
		}
		if (isLava) {
			return cubeLavaModel != null ? cubeLavaModel : cubeModel;
		}
		if (FluidDebrisModels.isIceStyle(id) && packedIce && packedIceCubeModel != null) {
			return packedIceCubeModel;
		}
		return cubeModel;
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
		final float phaseJitter;
		int slot;
		int slotX;
		int slotZ;

		FloatingDebris(Vec3 pos, FluidState fluidState, BlockPos originBlock, float size, int lifetime,
				DebrisMotion motion, boolean packedIce, int slot, int slotX, int slotZ) {
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
			this.slot = slot;
			this.slotX = slotX;
			this.slotZ = slotZ;
			this.phaseJitter = motion.bobPhase * 0.15F;
		}

		long slotKey() {
			return BlockPos.asLong(slotX, slot, slotZ);
		}

		boolean tick(Level level) {
			age++;
			prevPos = pos;
			
			if (age >= lifetime) {
				return false;
			}
			
			DebrisWorldQuery.Surface surface = DebrisWorldQuery.findTopFluidSurface(
				level, BlockPos.containing(pos));
			if (surface == null) {
				return false;
			}
			
			fluidState = surface.fluid();
			originBlock = surface.pos();
			
			Vec3 flow = fluidState.getFlow(level, originBlock);
			float flowSpeed = (float) flow.length();
			velocity = new Vec3(
				velocity.x * 0.88 + flow.x * 0.02,
				velocity.y,
				velocity.z * 0.88 + flow.z * 0.02
			);
			
			AABB box = DebrisWorldQuery.cubeBox(pos.x, pos.y, pos.z, size);
			int fromY = originBlock.getY();
			
			double dx = velocity.x;
			if (!DebrisWorldQuery.canEnterColumn(level, pos.x + dx, pos.z, fromY)) {
				dx = 0;
				velocity = new Vec3(-velocity.x * WALL_BOUNCE, velocity.y, velocity.z);
				collisionsThisSecond++;
			} else {
				double clippedX = DebrisWorldQuery.collideAxis(level, box, Direction.Axis.X, dx);
				if (Math.abs(clippedX) + 1.0e-4 < Math.abs(dx)) {
					velocity = new Vec3(-velocity.x * WALL_BOUNCE, velocity.y, velocity.z);
					collisionsThisSecond++;
				}
				dx = clippedX;
			}
			pos = new Vec3(pos.x + dx, pos.y, pos.z);
			box = DebrisWorldQuery.cubeBox(pos.x, pos.y, pos.z, size);
			
			double dz = velocity.z;
			if (!DebrisWorldQuery.canEnterColumn(level, pos.x, pos.z + dz, fromY)) {
				dz = 0;
				velocity = new Vec3(velocity.x, velocity.y, -velocity.z * WALL_BOUNCE);
				collisionsThisSecond++;
			} else {
				double clippedZ = DebrisWorldQuery.collideAxis(level, box, Direction.Axis.Z, dz);
				if (Math.abs(clippedZ) + 1.0e-4 < Math.abs(dz)) {
					velocity = new Vec3(velocity.x, velocity.y, -velocity.z * WALL_BOUNCE);
					collisionsThisSecond++;
				}
				dz = clippedZ;
			}
			pos = new Vec3(pos.x, pos.y, pos.z + dz);
			
			surface = DebrisWorldQuery.findTopFluidSurface(level, BlockPos.containing(pos));
			if (surface == null) {
				return false;
			}
			fluidState = surface.fluid();
			originBlock = surface.pos();
			
			Vec3 flowNow = fluidState.getFlow(level, originBlock);
			float flowNowSpeed = (float) flowNow.length();
			Vec3 waveDir = DebrisWaveField.waveDirection(flowNow);
			float waveAmp = SimurailConfig.client().fluidVisualsWaveAmplitude.get().floatValue();
			float waveLen = SimurailConfig.client().fluidVisualsWaveLength.get().floatValue();
			float waveSpd = SimurailConfig.client().fluidVisualsWaveSpeed.get().floatValue();
			double phase = DebrisWaveField.phase(pos.x, pos.z, waveDir, flowNowSpeed, tickCounter,
				phaseJitter, waveLen, waveSpd);
			double waveH = DebrisWaveField.height(phase, waveAmp, flowNowSpeed);
			if (SimurailConfig.client().fluidVisualsEntityInteraction.get()) {
				waveH += occupants.wakeHeight(pos.x, pos.z, waveLen);
			}
			
			double restingY = surface.y() + DebrisWorldQuery.submergeOffset(size);
			float floorAmp = Math.max(DebrisWorldQuery.bobAmplitude(size), waveAmp);
			double floatY = restingY + waveH;
			boolean falling = DebrisWorldQuery.isDrop(surface, pos.y) || pos.y > restingY + 0.5 + waveAmp;
			
			if (falling) {
				velocity = new Vec3(velocity.x, Math.max(velocity.y - GRAVITY, -TERMINAL_VELOCITY), velocity.z);
			} else if (pos.y < restingY - 0.25) {
				velocity = new Vec3(velocity.x, Math.max(velocity.y, 0.08), velocity.z);
			} else {
				velocity = new Vec3(velocity.x, (floatY - pos.y) * 0.35, velocity.z);
			}
			
			box = DebrisWorldQuery.cubeBox(pos.x, pos.y, pos.z, size);
			double dy = velocity.y;
			double clippedY = DebrisWorldQuery.collideAxis(level, box, Direction.Axis.Y, dy);
			if (dy < 0 && Math.abs(clippedY) + 1.0e-4 < Math.abs(dy)) {
				DebrisWorldQuery.Surface floorFluid = DebrisWorldQuery.findTopFluidSurface(
					level, BlockPos.containing(pos.x, pos.y + clippedY, pos.z));
				if (floorFluid == null) {
					return false;
				}
				collisionsThisSecond++;
				velocity = new Vec3(velocity.x, 0, velocity.z);
			}
			pos = new Vec3(pos.x, pos.y + clippedY, pos.z);
			
			if (pos.y < restingY - floorAmp) {
				pos = new Vec3(pos.x, restingY - floorAmp, pos.z);
				if (velocity.y < 0) {
					velocity = new Vec3(velocity.x, 0, velocity.z);
				}
			}
			
			if (SimurailConfig.client().fluidVisualsEntityInteraction.get()) {
				Vec3[] posHolder = { pos };
				Vec3[] velHolder = { velocity };
				int contacts = occupants.pushOut(posHolder, velHolder, size);
				if (contacts > 0) {
					entityContactsThisSecond += contacts;
					pos = posHolder[0];
					velocity = velHolder[0];
				}
			}
			
			float spinSpeed = SimurailConfig.client().fluidVisualsDebrisSpinSpeed.get().floatValue();
			DebrisWaveField.rollRotation(WAVE_ROLL, waveDir, phase, spinSpeed);
			motion.setWaveRotation(WAVE_ROLL);
			
			double dxPlayer = pos.x - playerPosThisTick.x;
			double dzPlayer = pos.z - playerPosThisTick.z;
			int radius = SimurailConfig.client().fluidVisualsRenderRadius.get();
			if (dxPlayer * dxPlayer + dzPlayer * dzPlayer > (double) radius * radius) {
				return false;
			}
			
			return true;
		}

		boolean tryWrapUpstream(Level level, int radius) {
			Vec3 dir = DebrisWaveField.waveDirection(fluidState.getFlow(level, originBlock));
			double tx = playerPosThisTick.x - dir.x * radius * 0.85;
			double tz = playerPosThisTick.z - dir.z * radius * 0.85;
			DebrisWorldQuery.Surface surface = DebrisWorldQuery.findTopFluidInColumn(
				level, Mth.floor(tx), Mth.floor(tz), Mth.floor(playerPosThisTick.y));
			if (surface == null) {
				for (int ox = -2; ox <= 2 && surface == null; ox++) {
					for (int oz = -2; oz <= 2 && surface == null; oz++) {
						surface = DebrisWorldQuery.findTopFluidInColumn(
							level, Mth.floor(tx) + ox, Mth.floor(tz) + oz, Mth.floor(playerPosThisTick.y));
					}
				}
			}
			if (surface == null) {
				return false;
			}
			double spawnY = surface.y() + DebrisWorldQuery.submergeOffset(size);
			double x = surface.pos().getX() + 0.5;
			double z = surface.pos().getZ() + 0.5;
			if (!DebrisWorldQuery.cubeFits(level, x, spawnY, z, size)) {
				return false;
			}
			pos = new Vec3(x, spawnY, z);
			prevPos = pos;
			velocity = Vec3.ZERO;
			fluidState = surface.fluid();
			originBlock = surface.pos();
			age = 0;
			slotX = surface.pos().getX();
			slotZ = surface.pos().getZ();
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
			if (SimurailConfig.client().fluidVisualsEntityInteraction.get()
					&& occupants.insideAny(renderPos.x, renderPos.y, renderPos.z, size)) {
				return;
			}
			
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
			String debrisModelId;
			try {
				debrisModelId = SimurailConfig.client().fluidVisualsDebrisModel.get();
			} catch (Exception e) {
				debrisModelId = FluidDebrisModels.WATER_CUBE;
			}
			boolean iceStyle = FluidDebrisModels.isIceStyle(debrisModelId);
			
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
				if (iceStyle) {
					r = (255 * 3 + wr) / 4;
					g = (255 * 3 + wg) / 4;
					b = (255 * 3 + wb) / 4;
					a = packedIce ? 230 : 200;
				} else {
					r = wr;
					g = wg;
					b = wb;
					a = 200;
				}
				color = (a << 24) | (r << 16) | (g << 8) | b;
				light = LevelRenderer.getLightColor(level, BlockPos.containing(renderPos));
				renderType = RenderType.translucent();
			} else if (fluidState.is(FluidTags.LAVA)) {
				color = 0xFFFFFFFF;
				light = 0xF000F0;
				renderType = iceStyle ? RenderType.solid() : RenderType.cutout();
			} else {
				color = 0xD93232FF;
				light = LevelRenderer.getLightColor(level, BlockPos.containing(renderPos));
				renderType = RenderType.translucent();
			}
			
			FluidDebrisModels.renderQuads(poseStack, bufferSource, model, color, light, renderType);
			poseStack.popPose();
		}
	}
}
