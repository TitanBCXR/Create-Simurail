package com.crystaelix.simurail.config;

public class SimurailClientConfig extends SimurailBaseConfig {

	public final ConfigGroup bogey = group(0, "bogey", "Physics Bogies");

	public final ConfigGroup wheelSlip = group(1, "wheelSlip", "Wheel Slip VFX");
	public final ConfigBool wheelSlipSparkEnabled = b(true, "sparkEnabled", Comments.wheelSlipSparkEnabled);
	public final ConfigFloat wheelSlipSparkDensity = f(1, 0, 4, "sparkDensity", Comments.wheelSlipSparkDensity);
	public final ConfigFloat wheelSlipSparkScale = f(1, 0.25F, 4, "sparkScale", Comments.wheelSlipSparkScale);

	public final ConfigGroup fluidVisuals = group(1, "fluidVisuals", "Fluid Visuals");
	public final ConfigBool fluidVisualsEnabled = b(false, "enabled", Comments.fluidVisualsEnabled);
	public final ConfigInt fluidVisualsCubesPerBlock = i(8, 1, 27, "cubesPerBlock", Comments.fluidVisualsCubesPerBlock);
	public final ConfigInt fluidVisualsRenderRadius = i(16, 4, 64, "renderRadius", Comments.fluidVisualsRenderRadius);
	public final ConfigInt fluidVisualsMaxCubes = i(4096, 256, 32768, "maxCubes", Comments.fluidVisualsMaxCubes);
	public final ConfigBool fluidVisualsReplaceVanilla = b(false, "replaceVanillaFluid", Comments.fluidVisualsReplaceVanilla);

	public SimurailClientConfig() {
	}

	@Override
	public String getName() {
		return "client";
	}

	static class Comments {
		static String wheelSlipSparkEnabled = "Spawn sparks between the wheels of a Physics Bogie and the track when the wheels lose traction.";
		static String wheelSlipSparkDensity = "Multiplier on the amount of sparks spawned by slipping wheels of a Physics Bogie.";
		static String wheelSlipSparkScale = "Multiplier on the size of the sparks spawned by slipping wheels of a Physics Bogie.";

		static String fluidVisualsEnabled = "Render water and lava as flowing cubes instead of (or overlaid on) vanilla fluid rendering.";
		static String fluidVisualsCubesPerBlock = "Number of cubes per fluid block. 1=1 cube (1x1x1), 8=8 cubes (2x2x2), 27=27 cubes (3x3x3). Higher values look smoother but cost more performance.";
		static String fluidVisualsRenderRadius = "Maximum distance in blocks to render flowing cubes around the player.";
		static String fluidVisualsMaxCubes = "Maximum number of cubes to render at once. Farthest cubes are dropped first when limit is reached.";
		static String fluidVisualsReplaceVanilla = "Hide vanilla fluid rendering when flowing cubes are enabled. If false, cubes overlay on vanilla fluids.";
	}
}
