package com.crystaelix.simurail.config;

import net.createmod.catnip.config.ConfigBase;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;

public class SimurailClientConfig extends SimurailBaseConfig {

	public final ConfigGroup bogey = group(0, "bogey", "Physics Bogies");

	public final ConfigGroup wheelSlip = group(0, "wheelSlip", "Wheel Slip VFX");
	public final ConfigBool wheelSlipSparkEnabled = b(true, "sparkEnabled", Comments.wheelSlipSparkEnabled);
	public final ConfigFloat wheelSlipSparkDensity = f(1, 0, 4, "sparkDensity", Comments.wheelSlipSparkDensity);
	public final ConfigFloat wheelSlipSparkScale = f(1, 0.25F, 4, "sparkScale", Comments.wheelSlipSparkScale);

	public final ConfigGroup fluidVisuals = group(0, "fluidVisuals", "Fluid Visuals");
	public final ConfigInt fluidVisualsRenderStyle = i(1, 0, 3, "renderStyle", Comments.fluidVisualsRenderStyle);
	public final ConfigBase.CValue<String, ConfigValue<String>> fluidVisualsDebrisModel = new CValue<>("debrisModel",
		builder -> builder.define("debrisModel", "builtin:water_cube"), Comments.fluidVisualsDebrisModel);
	public final ConfigFloat fluidVisualsDensity = f(0.5F, 0.1F, 4.0F, "density", Comments.fluidVisualsDensity);
	public final ConfigFloat fluidVisualsDebrisScale = f(0.25F, 0.1F, 1.0F, "debrisScale", Comments.fluidVisualsDebrisScale);
	public final ConfigFloat fluidVisualsDebrisSpinSpeed = f(1.0F, 0.0F, 2.0F, "debrisSpinSpeed", Comments.fluidVisualsDebrisSpinSpeed);
	public final ConfigFloat fluidVisualsWaveAmplitude = f(0.25F, 0.0F, 1.0F, "waveAmplitude", Comments.fluidVisualsWaveAmplitude);
	public final ConfigFloat fluidVisualsWaveLength = f(3.0F, 1.0F, 8.0F, "waveLength", Comments.fluidVisualsWaveLength);
	public final ConfigFloat fluidVisualsWaveSpeed = f(1.0F, 0.0F, 2.0F, "waveSpeed", Comments.fluidVisualsWaveSpeed);
	public final ConfigBool fluidVisualsEntityInteraction = b(true, "entityInteraction", Comments.fluidVisualsEntityInteraction);
	public final ConfigFloat fluidVisualsWakeStrength = f(1.0F, 0.0F, 2.0F, "wakeStrength", Comments.fluidVisualsWakeStrength);
	public final ConfigInt fluidVisualsRenderRadius = i(16, 4, 64, "renderRadius", Comments.fluidVisualsRenderRadius);
	public final ConfigInt fluidVisualsMaxCubes = i(512, 64, 4096, "maxCubes", Comments.fluidVisualsMaxCubes);
	public final ConfigInt fluidVisualsMaxTriangles = i(100000, 10000, 1000000, "maxTriangles", Comments.fluidVisualsMaxTriangles);
	public final ConfigBool fluidVisualsReplaceVanilla = b(false, "replaceVanillaFluid", Comments.fluidVisualsReplaceVanilla);
	public final ConfigBool fluidVisualsDebugLogging = b(false, "debugLogging", Comments.fluidVisualsDebugLogging);

	public SimurailClientConfig() {
	}

	@Override
	public void onLoad() {
		super.onLoad();
		migrateDebrisModel();
	}

	@Override
	public void onReload() {
		super.onReload();
		migrateDebrisModel();
	}

	private void migrateDebrisModel() {
		try {
			int style = fluidVisualsRenderStyle.get();
			String model = fluidVisualsDebrisModel.get();
			boolean changed = false;
			if (model == null || model.isBlank() || "builtin:water_cube".equals(model)) {
				if (style == 2) {
					fluidVisualsDebrisModel.set("builtin:ice_cube");
					changed = true;
				} else if (style == 3) {
					fluidVisualsDebrisModel.set("builtin:droplet");
					changed = true;
				}
			}
			if (style >= 2) {
				fluidVisualsRenderStyle.set(1);
				changed = true;
			}
			if (changed && specification != null) {
				specification.save();
			}
		} catch (Exception e) {
		}
	}

	@Override
	public String getName() {
		return "client";
	}

	static class Comments {
		static String wheelSlipSparkEnabled = "Spawn sparks between the wheels of a Physics Bogie and the track when the wheels lose traction.";
		static String wheelSlipSparkDensity = "Multiplier on the amount of sparks spawned by slipping wheels of a Physics Bogie.";
		static String wheelSlipSparkScale = "Multiplier on the size of the sparks spawned by slipping wheels of a Physics Bogie.";

		static String fluidVisualsRenderStyle = "Floating debris master switch. 0=OFF, 1=ON. Old 2=ICE_CUBE and 3=DROPLET migrate into debrisModel.";
		static String fluidVisualsDebrisModel = "Debris model id: builtin:water_cube, builtin:ice_cube, builtin:droplet, builtin:tile, or custom:<name> from config/simurail/fluid_models/. Missing custom models fall back to water_cube.";
		static String fluidVisualsDensity = "Cubes per surface block. 0.25 = sparse, 0.5 = moderate (default), 1.0 = dense, 2.0+ = very dense.";
		static String fluidVisualsDebrisScale = "World-space cube edge in blocks. 0.25 = small wave cubes (default), 0.1 = tiny, 1.0 = huge. Applied via PoseStack.";
		static String fluidVisualsDebrisSpinSpeed = "Roll speed multiplier for the travelling wave. 0.0 = no roll, 1.0 = default, 2.0 = faster.";
		static String fluidVisualsWaveAmplitude = "Wave height in blocks. 0 = flat, 0.25 = moderate (default), 1.0 = huge. Still water uses a lower swell.";
		static String fluidVisualsWaveLength = "Wavelength in blocks. 3 = default. Shorter = choppier, longer = ocean swell.";
		static String fluidVisualsWaveSpeed = "Wave travel speed multiplier. 0 = frozen, 1.0 = default, 2.0 = fast. Scales up with flow.";
		static String fluidVisualsEntityInteraction = "Cubes part around players/mobs/boats/items/trains and pick up a wake. Visual only; cubes never push entities.";
		static String fluidVisualsWakeStrength = "Strength of entity bow-wave / wake ripples. 0 = none, 1.0 = default, 2.0 = strong.";
		static String fluidVisualsRenderRadius = "Maximum distance in blocks to render floating cubes around the player.";
		static String fluidVisualsMaxCubes = "Maximum number of cubes to render at once. Farthest cubes are removed first when limit is reached.";
		static String fluidVisualsMaxTriangles = "Maximum total triangle count for all rendered fluid meshes. Reduces cube count automatically if mesh * cubes exceeds this budget.";
		static String fluidVisualsReplaceVanilla = "Hide vanilla fluid rendering (not recommended). If false (default), cubes overlay on vanilla fluids.";
		static String fluidVisualsDebugLogging = "Log fluid renderer diagnostics: collisions/despawns/entity contacts per second, plus active/spawn stats every 5 seconds.";
	}
}
