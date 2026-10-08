package com.crystaelix.simurail.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.SharedConstants;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Scans {@code config/simurail/fluid_models/} and maps files into virtual pack paths.
 */
public final class FluidDebrisCatalog {

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public static final String CUSTOM_MODEL_PREFIX = "models/fluid_debris/custom/";
	public static final String CUSTOM_TEXTURE_PREFIX = "textures/fluid_debris/custom/";
	public static final String NAMESPACE = "simurail";
	public static final int MAX_TRIANGLES = 256;

	public static final class DiscoveredModel {
		public final String sanitizedName;
		public final String displayName;
		public final boolean obj;
		public final boolean hasTexture;
		public final boolean lavaVariant;

		DiscoveredModel(String sanitizedName, String displayName, boolean obj, boolean hasTexture, boolean lavaVariant) {
			this.sanitizedName = sanitizedName;
			this.displayName = displayName;
			this.obj = obj;
			this.hasTexture = hasTexture;
			this.lavaVariant = lavaVariant;
		}

		public String customId() {
			return "custom:" + sanitizedName;
		}

		public ResourceLocation modelLocation() {
			return ResourceLocation.fromNamespaceAndPath(NAMESPACE, "fluid_debris/custom/" + sanitizedName);
		}
	}

	private static final Object LOCK = new Object();
	private static Map<ResourceLocation, IoSupplier<InputStream>> resources = Map.of();
	private static List<DiscoveredModel> models = List.of();

	private FluidDebrisCatalog() {
	}

	public static Path modelsFolder() {
		return FMLPaths.CONFIGDIR.get().resolve("simurail").resolve("fluid_models");
	}

	public static Path readmePath() {
		return FMLPaths.CONFIGDIR.get().resolve("simurail").resolve("README_fluid_models.txt");
	}

	public static void ensureFolderAndReadme() {
		try {
			Files.createDirectories(modelsFolder());
			Files.writeString(readmePath(), README, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.warn("[Simurail] Could not create fluid_models folder or README: {}", e.getMessage());
		}
	}

	public static List<DiscoveredModel> models() {
		return models;
	}

	public static List<DiscoveredModel> pickerModels() {
		List<DiscoveredModel> out = new ArrayList<>();
		for (DiscoveredModel model : models) {
			if (model.lavaVariant) {
				String base = model.sanitizedName.substring(0, model.sanitizedName.length() - "_lava".length());
				if (hasModel(base)) {
					continue;
				}
			}
			out.add(model);
		}
		return out;
	}

	public static Map<ResourceLocation, IoSupplier<InputStream>> resources() {
		return resources;
	}

	public static boolean hasModel(String sanitizedName) {
		for (DiscoveredModel model : models) {
			if (model.sanitizedName.equals(sanitizedName)) {
				return true;
			}
		}
		return false;
	}

	public static void scan() {
		ensureFolderAndReadme();
		synchronized (LOCK) {
			Map<ResourceLocation, IoSupplier<InputStream>> nextResources = new LinkedHashMap<>();
			List<DiscoveredModel> nextModels = new ArrayList<>();
			try {
				scanUnlocked(nextResources, nextModels);
			} catch (Exception e) {
				LOGGER.warn("[Simurail] Failed to scan custom fluid debris models: {}", e.toString());
			}
			resources = Map.copyOf(nextResources);
			models = List.copyOf(nextModels);
		}
	}

	private static void scanUnlocked(Map<ResourceLocation, IoSupplier<InputStream>> outResources,
			List<DiscoveredModel> outModels) throws IOException {
		Path folder = modelsFolder();
		if (!Files.isDirectory(folder)) {
			putGeneratedMeta(outResources, List.of());
			return;
		}

		Map<String, Path> pngs = new TreeMap<>();
		Map<String, Path> jsons = new TreeMap<>();
		Map<String, Path> objs = new TreeMap<>();
		Map<String, Path> mtls = new TreeMap<>();
		Map<String, String> displayNames = new HashMap<>();

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
			for (Path path : stream) {
				if (!Files.isRegularFile(path)) {
					continue;
				}
				String filename = path.getFileName().toString();
				if (filename.startsWith(".") || filename.startsWith("README")) {
					continue;
				}
				int dot = filename.lastIndexOf('.');
				if (dot <= 0) {
					continue;
				}
				String stem = filename.substring(0, dot);
				String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
				String sanitized = uniqueSanitized(stem, displayNames, ext);
				if (sanitized == null) {
					continue;
				}
				switch (ext) {
					case "png" -> pngs.put(sanitized, path);
					case "json" -> jsons.put(sanitized, path);
					case "obj" -> objs.put(sanitized, path);
					case "mtl" -> mtls.put(sanitized, path);
					default -> {
					}
				}
			}
		}

		Set<String> pngNames = pngs.keySet();
		for (Map.Entry<String, Path> entry : pngs.entrySet()) {
			putFile(outResources, NAMESPACE, CUSTOM_TEXTURE_PREFIX + entry.getKey() + ".png", entry.getValue());
		}

		for (Map.Entry<String, Path> entry : mtls.entrySet()) {
			String name = entry.getKey();
			String rewritten = rewriteMtl(readText(entry.getValue()));
			putBytes(outResources, NAMESPACE, CUSTOM_MODEL_PREFIX + name + ".mtl", rewritten);
		}

		for (Map.Entry<String, Path> entry : objs.entrySet()) {
			String name = entry.getKey();
			boolean hasTexture = pngs.containsKey(name) || referencesLocalTexture(name, pngNames);
			String rewritten = rewriteObj(readText(entry.getValue()), name);
			putBytes(outResources, NAMESPACE, CUSTOM_MODEL_PREFIX + name + ".obj", rewritten);
			if (!mtls.containsKey(name)) {
				putBytes(outResources, NAMESPACE, CUSTOM_MODEL_PREFIX + name + ".mtl", generatedMtl());
			}
			if (!jsons.containsKey(name)) {
				putBytes(outResources, NAMESPACE, CUSTOM_MODEL_PREFIX + name + ".json",
					generatedObjWrapper(name, hasTexture || pngs.containsKey(name)));
			}
			outModels.add(new DiscoveredModel(
				name,
				displayNames.getOrDefault(name, name),
				true,
				hasTexture || pngs.containsKey(name),
				name.endsWith("_lava")
			));
		}

		for (Map.Entry<String, Path> entry : jsons.entrySet()) {
			String name = entry.getKey();
			try {
				String rewritten = rewriteJson(readText(entry.getValue()), name, pngs, objs, pngNames);
				putBytes(outResources, NAMESPACE, CUSTOM_MODEL_PREFIX + name + ".json", rewritten);
				boolean hasTexture = pngs.containsKey(name) || jsonHasNonFluidTexture(rewritten);
				boolean already = false;
				for (DiscoveredModel existing : outModels) {
					if (existing.sanitizedName.equals(name)) {
						already = true;
						break;
					}
				}
				if (!already) {
					outModels.add(new DiscoveredModel(
						name,
						displayNames.getOrDefault(name, name),
						false,
						hasTexture,
						name.endsWith("_lava")
					));
				}
			} catch (Exception e) {
				LOGGER.warn("[Simurail] Skipping fluid debris model '{}': {}", entry.getValue().getFileName(), e.getMessage());
			}
		}

		putGeneratedMeta(outResources, pngs.keySet().stream().toList());
		LOGGER.info("[Simurail] Custom fluid debris models: {} ({} textures)", outModels.size(), pngs.size());
	}

	private static String uniqueSanitized(String stem, Map<String, String> displayNames, String ext) {
		String sanitized = sanitize(stem);
		if (sanitized == null) {
			LOGGER.warn("[Simurail] Skipping fluid debris file with invalid name '{}'", stem);
			return null;
		}
		if (!displayNames.containsKey(sanitized)) {
			displayNames.put(sanitized, stem);
			return sanitized;
		}
		if ("png".equals(ext) || "mtl".equals(ext) || displayNames.get(sanitized).equalsIgnoreCase(stem)) {
			return sanitized;
		}
		int n = 2;
		String candidate = sanitized + "_" + n;
		while (displayNames.containsKey(candidate)) {
			n++;
			candidate = sanitized + "_" + n;
		}
		displayNames.put(candidate, stem);
		return candidate;
	}

	public static String sanitize(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String s = raw.toLowerCase(Locale.ROOT);
		StringBuilder out = new StringBuilder(s.length());
		boolean lastUnderscore = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (ResourceLocation.validPathChar(c) && c != '/') {
				out.append(c);
				lastUnderscore = false;
			} else if (!lastUnderscore) {
				out.append('_');
				lastUnderscore = true;
			}
		}
		String sanitized = out.toString().replaceAll("^[._-]+", "").replaceAll("[._-]+$", "");
		if (sanitized.isEmpty() || !ResourceLocation.isValidPath(sanitized)) {
			return null;
		}
		return sanitized;
	}

	private static String readText(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	private static String rewriteObj(String obj, String name) {
		StringBuilder out = new StringBuilder(obj.length() + 32);
		boolean wroteMtl = false;
		for (String line : obj.split("\\R", -1)) {
			String trimmed = line.trim();
			if (trimmed.toLowerCase(Locale.ROOT).startsWith("mtllib")) {
				out.append("mtllib ").append(name).append(".mtl").append('\n');
				wroteMtl = true;
			} else {
				out.append(line).append('\n');
			}
		}
		if (!wroteMtl) {
			return "mtllib " + name + ".mtl\n" + out;
		}
		return out.toString();
	}

	private static String rewriteMtl(String mtl) {
		StringBuilder out = new StringBuilder(mtl.length() + 16);
		for (String line : mtl.split("\\R", -1)) {
			String trimmed = line.trim();
			String lower = trimmed.toLowerCase(Locale.ROOT);
			if (lower.startsWith("map_kd") || lower.startsWith("map_ka") || lower.startsWith("map_ks")) {
				out.append("map_Kd #particle\n");
			} else {
				out.append(line).append('\n');
			}
		}
		return out.toString();
	}

	private static String generatedMtl() {
		return "newmtl material\nKd 1.0 1.0 1.0\nmap_Kd #particle\n";
	}

	private static String generatedObjWrapper(String name, boolean hasTexture) {
		String texture = hasTexture
			? NAMESPACE + ":fluid_debris/custom/" + name
			: "minecraft:block/water_still";
		JsonObject json = new JsonObject();
		json.addProperty("loader", "neoforge:obj");
		json.addProperty("model", NAMESPACE + ":models/fluid_debris/custom/" + name + ".obj");
		json.addProperty("flip_v", true);
		json.addProperty("automatic_culling", false);
		json.addProperty("mtl_override", NAMESPACE + ":models/fluid_debris/custom/" + name + ".mtl");
		JsonObject textures = new JsonObject();
		textures.addProperty("particle", texture);
		json.add("textures", textures);
		return GSON.toJson(json);
	}

	private static String rewriteJson(String text, String name, Map<String, Path> pngs, Map<String, Path> objs,
			Set<String> pngNames) {
		JsonObject root = JsonParser.parseString(text).getAsJsonObject();
		boolean hasTexture = pngs.containsKey(name);
		String fallback = hasTexture
			? NAMESPACE + ":fluid_debris/custom/" + name
			: "minecraft:block/water_still";

		if (root.has("loader") && root.get("loader").isJsonPrimitive()
				&& root.get("loader").getAsString().contains("obj")) {
			if (objs.containsKey(name)) {
				root.addProperty("model", NAMESPACE + ":models/fluid_debris/custom/" + name + ".obj");
			} else if (root.has("model") && root.get("model").isJsonPrimitive()) {
				String modelRef = root.get("model").getAsString();
				String stem = basename(modelRef);
				String sanitized = sanitize(stem);
				if (sanitized != null && objs.containsKey(sanitized)) {
					root.addProperty("model", NAMESPACE + ":models/fluid_debris/custom/" + sanitized + ".obj");
				}
			}
			if (!root.has("flip_v")) {
				root.addProperty("flip_v", true);
			}
		}

		JsonObject textures;
		if (root.has("textures") && root.get("textures").isJsonObject()) {
			textures = root.getAsJsonObject("textures");
			List<String> keys = new ArrayList<>();
			for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
				keys.add(entry.getKey());
			}
			for (String key : keys) {
				JsonElement value = textures.get(key);
				if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
					continue;
				}
				String ref = value.getAsString();
				if (ref.startsWith("#")) {
					continue;
				}
				String local = localTextureRef(ref, pngNames);
				if (local != null) {
					textures.addProperty(key, NAMESPACE + ":fluid_debris/custom/" + local);
				}
			}
		} else {
			textures = new JsonObject();
			root.add("textures", textures);
		}
		if (!textures.has("particle")) {
			textures.addProperty("particle", fallback);
		}
		if (!hasTexture && !jsonHasNonFluidTexture(textures) && !textures.has("fluid") && !textures.has("all")) {
			textures.addProperty("all", fallback);
			textures.addProperty("fluid", fallback);
		}
		return GSON.toJson(root);
	}

	private static boolean jsonHasNonFluidTexture(String json) {
		try {
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			if (!root.has("textures") || !root.get("textures").isJsonObject()) {
				return false;
			}
			return jsonHasNonFluidTexture(root.getAsJsonObject("textures"));
		} catch (Exception e) {
			return false;
		}
	}

	private static boolean jsonHasNonFluidTexture(JsonObject textures) {
		for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
			if (!entry.getValue().isJsonPrimitive()) {
				continue;
			}
			String value = entry.getValue().getAsString();
			if (value.startsWith(NAMESPACE + ":fluid_debris/custom/")) {
				return true;
			}
		}
		return false;
	}

	private static String localTextureRef(String ref, Set<String> pngNames) {
		String stem = basename(ref);
		if (stem.endsWith(".png")) {
			stem = stem.substring(0, stem.length() - 4);
		}
		String sanitized = sanitize(stem);
		if (sanitized != null && pngNames.contains(sanitized)) {
			return sanitized;
		}
		return null;
	}

	private static boolean referencesLocalTexture(String name, Set<String> pngNames) {
		return pngNames.contains(name);
	}

	private static String basename(String path) {
		int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf(':'));
		return slash >= 0 ? path.substring(slash + 1) : path;
	}

	static String packMcmeta() {
		return """
			{
			  "pack": {
			    "pack_format": %d,
			    "description": "Simurail custom fluid debris models"
			  }
			}
			""".formatted(SharedConstants.RESOURCE_PACK_FORMAT);
	}

	private static void putGeneratedMeta(Map<ResourceLocation, IoSupplier<InputStream>> outResources, List<String> pngNames) {
		StringBuilder atlas = new StringBuilder("{\n  \"sources\": [\n");
		for (int i = 0; i < pngNames.size(); i++) {
			if (i > 0) {
				atlas.append(",\n");
			}
			atlas.append("    { \"type\": \"single\", \"resource\": \"")
				.append(NAMESPACE).append(":fluid_debris/custom/").append(pngNames.get(i)).append("\" }");
		}
		atlas.append("\n  ]\n}\n");
		putBytes(outResources, "minecraft", "atlases/blocks.json", atlas.toString());
	}

	private static void putFile(Map<ResourceLocation, IoSupplier<InputStream>> out, String namespace, String path, Path file) {
		out.put(ResourceLocation.fromNamespaceAndPath(namespace, path), IoSupplier.create(file));
	}

	private static void putBytes(Map<ResourceLocation, IoSupplier<InputStream>> out, String namespace, String path, String text) {
		out.put(ResourceLocation.fromNamespaceAndPath(namespace, path), bytes(text));
	}

	private static IoSupplier<InputStream> bytes(String text) {
		byte[] data = text.getBytes(StandardCharsets.UTF_8);
		return () -> new ByteArrayInputStream(data);
	}

	private static final String README = """
		Fluid Debris Custom Models
		==========================

		Drop Blockbench Java Block/Item JSON or Wavefront OBJ files in:

		  config/simurail/fluid_models/

		Supported:
		  - .json  Blockbench Java Block/Item models
		  - .obj   Wavefront meshes (optional matching .mtl)
		  - .png   Texture. Same base name as the model, or any PNG referenced
		           by the JSON/MTL in this folder.

		If there is no PNG, the model uses the fluid's own sprite with biome tint
		(same as the built-in water cube).

		Minecraft loads this folder through a built-in virtual resource pack:

		  assets/simurail/models/fluid_debris/custom/<name>.json
		  assets/simurail/textures/fluid_debris/custom/<name>.png

		OBJ files get an auto-generated wrapper with "loader": "neoforge:obj".
		Names are sanitised to [a-z0-9._-]. Models over 256 triangles after baking
		are skipped (a red "too many polys" row still appears in the picker).
		Failed loads are skipped and logged; they will not crash the game.

		Pick a model in Fluids (Client) in the Lightweight Physics config screen,
		or set client config:

		  debrisModel = "builtin:water_cube"
		  debrisModel = "builtin:ice_cube"
		  debrisModel = "builtin:droplet"
		  debrisModel = "builtin:tile"
		  debrisModel = "custom:<name>"

		Lava keeps the built-in lava cubes unless you also add a matching
		<name>_lava.json / .obj variant.

		Open the folder and Refresh from the config GUI (reloads resource packs).

		World size is still set by debrisScale (PoseStack), not JSON element size.

		--------------------------------------------------------------------
		Sample custom model (example only — not shipped). Save this yourself as
		config/simurail/fluid_models/tiny_wedge.json:

		{
		  "parent": "minecraft:block/block",
		  "textures": {
		    "particle": "minecraft:block/water_still",
		    "all": "minecraft:block/water_still"
		  },
		  "elements": [
		    {
		      "from": [4, 6, 6],
		      "to": [12, 10, 10],
		      "faces": {
		        "down":  {"uv": [4, 6, 12, 10], "texture": "#all", "tintindex": 0},
		        "up":    {"uv": [4, 6, 12, 10], "texture": "#all", "tintindex": 0},
		        "north": {"uv": [4, 6, 12, 10], "texture": "#all", "tintindex": 0},
		        "south": {"uv": [4, 6, 12, 10], "texture": "#all", "tintindex": 0},
		        "west":  {"uv": [6, 6, 10, 10], "texture": "#all", "tintindex": 0},
		        "east":  {"uv": [6, 6, 10, 10], "texture": "#all", "tintindex": 0}
		      }
		    }
		  ]
		}

		Optional companion texture: tiny_wedge.png in the same folder.
		For OBJ: tiny_wedge.obj (+ tiny_wedge.mtl, tiny_wedge.png). Blender FBX
		is not supported; export Wavefront OBJ (flip_v is applied for you).
		""";
}
