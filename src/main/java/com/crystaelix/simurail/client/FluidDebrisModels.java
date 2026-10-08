package com.crystaelix.simurail.client;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.crystaelix.simurail.config.SimurailConfig;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Built-in + custom debris models: register, bake, triangle-count, resolve by config id.
 */
public final class FluidDebrisModels {

	private static final Logger LOGGER = LogUtils.getLogger();
	public static final String WATER_CUBE = "builtin:water_cube";
	public static final String ICE_CUBE = "builtin:ice_cube";
	public static final String DROPLET = "builtin:droplet";
	public static final String TILE = "builtin:tile";

	public enum Status {
		OK,
		TOO_MANY,
		FAILED
	}

	public static final class PickerEntry {
		public final String id;
		public final String displayName;
		public final ModelResourceLocation modelLocation;
		public final ModelResourceLocation lavaLocation;
		public final boolean custom;
		public int triangles;
		public Status status = Status.OK;
		BakedModel baked;
		BakedModel bakedLava;

		PickerEntry(String id, String displayName, ModelResourceLocation modelLocation,
				ModelResourceLocation lavaLocation, boolean custom) {
			this.id = id;
			this.displayName = displayName;
			this.modelLocation = modelLocation;
			this.lavaLocation = lavaLocation;
			this.custom = custom;
		}

		public boolean usable() {
			return status == Status.OK;
		}
	}

	private static final List<PickerEntry> ENTRIES = new ArrayList<>();

	private FluidDebrisModels() {
	}

	public static List<PickerEntry> entries() {
		return ENTRIES;
	}

	public static PickerEntry byId(String id) {
		if (id != null) {
			for (PickerEntry entry : ENTRIES) {
				if (entry.id.equals(id)) {
					return entry;
				}
			}
		}
		String resolved = resolveId(id);
		for (PickerEntry entry : ENTRIES) {
			if (entry.id.equals(resolved)) {
				return entry;
			}
		}
		return builtin(WATER_CUBE);
	}

	public static PickerEntry builtin(String id) {
		for (PickerEntry entry : ENTRIES) {
			if (entry.id.equals(id)) {
				return entry;
			}
		}
		return ENTRIES.isEmpty() ? null : ENTRIES.get(0);
	}

	public static String resolveId(String id) {
		if (id == null || id.isBlank()) {
			return WATER_CUBE;
		}
		String trimmed = id.trim();
		if (trimmed.startsWith("custom:")) {
			String name = trimmed.substring("custom:".length());
			for (PickerEntry entry : ENTRIES) {
				if (entry.id.equals(trimmed) && entry.usable()) {
					return trimmed;
				}
				if (entry.custom && entry.id.equals("custom:" + name) && entry.usable()) {
					return entry.id;
				}
			}
			return WATER_CUBE;
		}
		return switch (trimmed) {
			case ICE_CUBE, DROPLET, TILE, WATER_CUBE -> trimmed;
			default -> WATER_CUBE;
		};
	}

	public static boolean isIceStyle(String id) {
		return ICE_CUBE.equals(resolveId(id));
	}

	public static void registerAdditional(ModelEvent.RegisterAdditional event) {
		FluidDebrisCatalog.scan();
		register(event, "fluid_debris/ice_cube");
		register(event, "fluid_debris/packed_ice_cube");
		register(event, "fluid_debris/cube");
		register(event, "fluid_debris/droplet");
		register(event, "fluid_debris/tile");
		register(event, "fluid_debris/ice_cube_lava");
		register(event, "fluid_debris/cube_lava");
		register(event, "fluid_debris/droplet_lava");
		register(event, "fluid_debris/tile_lava");
		for (FluidDebrisCatalog.DiscoveredModel model : FluidDebrisCatalog.models()) {
			try {
				event.register(ModelResourceLocation.standalone(model.modelLocation()));
			} catch (Exception e) {
				LOGGER.warn("[Simurail] Could not register custom debris model {}: {}", model.sanitizedName, e.getMessage());
			}
		}
	}

	private static void register(ModelEvent.RegisterAdditional event, String path) {
		event.register(ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("simurail", path)));
	}

	public static void onBakingCompleted(ModelEvent.BakingCompleted event) {
		ENTRIES.clear();
		addBuiltin("Water Cube", WATER_CUBE, "fluid_debris/cube", "fluid_debris/cube_lava", event);
		addBuiltin("Ice Cube", ICE_CUBE, "fluid_debris/ice_cube", "fluid_debris/ice_cube_lava", event);
		addBuiltin("Droplet", DROPLET, "fluid_debris/droplet", "fluid_debris/droplet_lava", event);
		addBuiltin("Tile", TILE, "fluid_debris/tile", "fluid_debris/tile_lava", event);

		BakedModel missing = event.getModelManager().getMissingModel();
		for (FluidDebrisCatalog.DiscoveredModel model : FluidDebrisCatalog.pickerModels()) {
			PickerEntry entry = new PickerEntry(
				model.customId(),
				model.displayName,
				ModelResourceLocation.standalone(model.modelLocation()),
				lavaLocation(model.sanitizedName),
				true
			);
			inspect(entry, event, missing);
			ENTRIES.add(entry);
		}
		logMissingConfiguredModel();
		FluidCubeRenderer.invalidateModels();
	}

	private static void logMissingConfiguredModel() {
		try {
			String id = SimurailConfig.client().fluidVisualsDebrisModel.get();
			if (id != null && id.startsWith("custom:") && WATER_CUBE.equals(resolveId(id))) {
				LOGGER.warn("[Simurail] Custom debris model '{}' is missing or unusable; falling back to {}", id, WATER_CUBE);
			}
		} catch (Exception e) {
		}
	}

	private static ModelResourceLocation lavaLocation(String name) {
		String lavaName = name.endsWith("_lava") ? name : name + "_lava";
		if (FluidDebrisCatalog.hasModel(lavaName)) {
			return ModelResourceLocation.standalone(
				ResourceLocation.fromNamespaceAndPath("simurail", "fluid_debris/custom/" + lavaName));
		}
		return ModelResourceLocation.standalone(
			ResourceLocation.fromNamespaceAndPath("simurail", "fluid_debris/cube_lava"));
	}

	private static void addBuiltin(String display, String id, String path, String lavaPath, ModelEvent.BakingCompleted event) {
		PickerEntry entry = new PickerEntry(
			id,
			display,
			ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("simurail", path)),
			ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("simurail", lavaPath)),
			false
		);
		inspect(entry, event, event.getModelManager().getMissingModel());
		ENTRIES.add(entry);
	}

	private static void inspect(PickerEntry entry, ModelEvent.BakingCompleted event, BakedModel missing) {
		try {
			BakedModel model = event.getModels().get(entry.modelLocation);
			if (model == null) {
				model = event.getModelManager().getModel(entry.modelLocation);
			}
			if (model == null || model == missing) {
				entry.status = Status.FAILED;
				entry.triangles = 0;
				entry.baked = null;
				LOGGER.warn("[Simurail] Debris model '{}' failed to load", entry.id);
				return;
			}
			entry.baked = model;
			if (entry.lavaLocation != null) {
				BakedModel lava = event.getModels().get(entry.lavaLocation);
				if (lava == null) {
					lava = event.getModelManager().getModel(entry.lavaLocation);
				}
				if (lava != null && lava != missing) {
					entry.bakedLava = lava;
				}
			}
			entry.triangles = countTriangles(model);
			if (entry.triangles > FluidDebrisCatalog.MAX_TRIANGLES) {
				entry.status = Status.TOO_MANY;
				LOGGER.warn("[Simurail] Debris model '{}' has {} triangles (max {}); skipped",
					entry.id, entry.triangles, FluidDebrisCatalog.MAX_TRIANGLES);
			} else {
				entry.status = Status.OK;
			}
		} catch (Exception e) {
			entry.status = Status.FAILED;
			entry.triangles = 0;
			LOGGER.warn("[Simurail] Debris model '{}' failed to bake: {}", entry.id, e.toString());
		}
	}

	public static int countTriangles(BakedModel model) {
		if (model == null) {
			return 0;
		}
		RandomSource random = RandomSource.create(42);
		int quads = 0;
		try {
			for (Direction direction : Direction.values()) {
				quads += model.getQuads(null, direction, random, ModelData.EMPTY, null).size();
			}
			quads += model.getQuads(null, null, random, ModelData.EMPTY, null).size();
		} catch (Exception e) {
			return 0;
		}
		return quads * 2;
	}

	public static BakedModel resolve(Minecraft mc, String id, boolean isLava, boolean packedIce) {
		PickerEntry entry = byId(id);
		if (entry == null) {
			return fallback(mc, isLava);
		}
		if (isLava) {
			if (entry.custom) {
				if (entry.bakedLava != null && usableModel(mc, entry.bakedLava)
						&& FluidDebrisCatalog.hasModel(lavaName(entry))) {
					return entry.bakedLava;
				}
				return fallback(mc, true);
			}
			return entry.bakedLava != null ? entry.bakedLava : fallback(mc, true);
		}
		if (!entry.custom && ICE_CUBE.equals(entry.id) && packedIce) {
			BakedModel packed = mc.getModelManager().getModel(ModelResourceLocation.standalone(
				ResourceLocation.fromNamespaceAndPath("simurail", "fluid_debris/packed_ice_cube")));
			if (packed != null && packed != mc.getModelManager().getMissingModel()) {
				return packed;
			}
		}
		if (entry.custom && !entry.usable()) {
			return fallback(mc, false);
		}
		return entry.baked != null ? entry.baked : fallback(mc, false);
	}

	private static String lavaName(PickerEntry entry) {
		String name = entry.id.startsWith("custom:") ? entry.id.substring("custom:".length()) : entry.id;
		return name.endsWith("_lava") ? name : name + "_lava";
	}

	private static boolean usableModel(Minecraft mc, BakedModel model) {
		if (model == null || model == mc.getModelManager().getMissingModel()) {
			return false;
		}
		int triangles = countTriangles(model);
		return triangles > 0 && triangles <= FluidDebrisCatalog.MAX_TRIANGLES;
	}

	private static BakedModel fallback(Minecraft mc, boolean lava) {
		return mc.getModelManager().getModel(ModelResourceLocation.standalone(
			ResourceLocation.fromNamespaceAndPath("simurail", lava ? "fluid_debris/cube_lava" : "fluid_debris/cube")));
	}

	public static BakedModel previewModel(Minecraft mc, String id) {
		PickerEntry entry = byId(id);
		if (entry == null) {
			return null;
		}
		if (entry.baked != null) {
			return entry.baked;
		}
		BakedModel model = mc.getModelManager().getModel(entry.modelLocation);
		if (model == null || model == mc.getModelManager().getMissingModel()) {
			return null;
		}
		return model;
	}

	public static void renderQuads(PoseStack poseStack, MultiBufferSource bufferSource, BakedModel model,
			int color, int light, RenderType renderType) {
		if (model == null) {
			return;
		}
		VertexConsumer buffer = bufferSource.getBuffer(renderType);
		RandomSource random = RandomSource.create(42);
		float r = (color >> 16 & 0xFF) / 255f;
		float g = (color >> 8 & 0xFF) / 255f;
		float b = (color & 0xFF) / 255f;
		float a = (color >> 24 & 0xFF) / 255f;
		try {
			for (Direction direction : Direction.values()) {
				List<BakedQuad> quads = model.getQuads(null, direction, random, ModelData.EMPTY, renderType);
				for (BakedQuad quad : quads) {
					buffer.putBulkData(poseStack.last(), quad, r, g, b, a, light, 0x00F000F0);
				}
			}
			List<BakedQuad> unculled = model.getQuads(null, null, random, ModelData.EMPTY, renderType);
			for (BakedQuad quad : unculled) {
				buffer.putBulkData(poseStack.last(), quad, r, g, b, a, light, 0x00F000F0);
			}
		} catch (Exception e) {
			LOGGER.warn("[Simurail] Failed to render debris model: {}", e.getMessage());
		}
	}
}
