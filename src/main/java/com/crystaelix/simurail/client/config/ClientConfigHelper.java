package com.crystaelix.simurail.client.config;

import com.crystaelix.simurail.config.FluidRenderStyle;
import com.crystaelix.simurail.config.LightweightPhysicsConfigValues;
import com.crystaelix.simurail.config.SimurailConfig;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-only helper to load/save client visual config values.
 * This class is only referenced from client-side code to avoid classloading issues on servers.
 */
@OnlyIn(Dist.CLIENT)
public class ClientConfigHelper {
	
	public static void loadClientValues(LightweightPhysicsConfigValues values) {
		values.fluidRenderStyle = SimurailConfig.client().fluidVisualsRenderStyle.get();
		values.fluidCubesPerBlock = SimurailConfig.client().fluidVisualsCubesPerBlock.get();
		values.fluidRenderRadius = SimurailConfig.client().fluidVisualsRenderRadius.get();
		values.fluidMaxCubes = SimurailConfig.client().fluidVisualsMaxCubes.get();
		values.fluidMaxTriangles = SimurailConfig.client().fluidVisualsMaxTriangles.get();
		values.fluidReplaceVanilla = SimurailConfig.client().fluidVisualsReplaceVanilla.get();
	}
	
	public static void applyClientValues(LightweightPhysicsConfigValues values) {
		SimurailConfig.client().fluidVisualsRenderStyle.set(values.fluidRenderStyle);
		SimurailConfig.client().fluidVisualsCubesPerBlock.set(values.fluidCubesPerBlock);
		SimurailConfig.client().fluidVisualsRenderRadius.set(values.fluidRenderRadius);
		SimurailConfig.client().fluidVisualsMaxCubes.set(values.fluidMaxCubes);
		SimurailConfig.client().fluidVisualsMaxTriangles.set(values.fluidMaxTriangles);
		SimurailConfig.client().fluidVisualsReplaceVanilla.set(values.fluidReplaceVanilla);
	}
}

