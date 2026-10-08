package com.crystaelix.simurail.config;

public class SimurailClientConfig extends SimurailBaseConfig {

	public final ConfigGroup bogey = group(0, "bogey", "Physics Bogies");

	public final ConfigGroup wheelSlip = group(0, "wheelSlip", "Wheel Slip VFX");
	public final ConfigBool wheelSlipSparkEnabled = b(true, "sparkEnabled", Comments.wheelSlipSparkEnabled);
	public final ConfigFloat wheelSlipSparkDensity = f(1, 0, 4, "sparkDensity", Comments.wheelSlipSparkDensity);
	public final ConfigFloat wheelSlipSparkScale = f(1, 0.25F, 4, "sparkScale", Comments.wheelSlipSparkScale);

	public final ConfigGroup fluidVisuals = group(0, "fluidVisuals", "Fluid Visuals");
	public final ConfigInt fluidVisualsRenderStyle = i(1, 0, 3, "renderStyle", Comments.fluidVisualsRenderStyle);
	public final ConfigFloat fluidVisualsDensity = f(0.5F, 0.1F, 4.0F, "density", Comments.fluidVisualsDensity);
	public final ConfigInt fluidVisualsRenderRadius = i(16, 4, 64, "renderRadius", Comments.fluidVisualsRenderRadius);
	public final ConfigInt fluidVisualsMaxCubes = i(512, 64, 4096, "maxCubes", Comments.fluidVisualsMaxCubes);
	public final ConfigInt fluidVisualsMaxTriangles = i(100000, 10000, 1000000, "maxTriangles", Comments.fluidVisualsMaxTriangles);
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

		static String fluidVisualsRenderStyle = "Fluid rendering style. 0=OFF, 1=CUBE (default), 2=DROPLET, 3=TILE. Cubes float on the water surface and drift with the current.";
		static String fluidVisualsDensity = "Cubes per surface block. 0.25 = sparse, 0.5 = moderate (default), 1.0 = dense, 2.0+ = very dense.";
		static String fluidVisualsRenderRadius = "Maximum distance in blocks to render floating cubes around the player.";
		static String fluidVisualsMaxCubes = "Maximum number of cubes to render at once. Farthest cubes are removed first when limit is reached.";
		static String fluidVisualsMaxTriangles = "Maximum total triangle count for all rendered fluid meshes. Reduces cube count automatically if mesh * cubes exceeds this budget.";
		static String fluidVisualsReplaceVanilla = "Hide vanilla fluid rendering (not recommended). If false (default), cubes overlay on vanilla fluids.";
	}
}
