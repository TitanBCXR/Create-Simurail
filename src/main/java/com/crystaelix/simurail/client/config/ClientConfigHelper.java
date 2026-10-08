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
		values.fluidDensity = SimurailConfig.client().fluidVisualsDensity.get().floatValue();
		values.fluidDebrisScale = SimurailConfig.client().fluidVisualsDebrisScale.get().floatValue();
		values.fluidDebrisSpinSpeed = SimurailConfig.client().fluidVisualsDebrisSpinSpeed.get().floatValue();
		values.fluidWaveAmplitude = SimurailConfig.client().fluidVisualsWaveAmplitude.get().floatValue();
		values.fluidWaveLength = SimurailConfig.client().fluidVisualsWaveLength.get().floatValue();
		values.fluidWaveSpeed = SimurailConfig.client().fluidVisualsWaveSpeed.get().floatValue();
		values.fluidEntityInteraction = SimurailConfig.client().fluidVisualsEntityInteraction.get();
		values.fluidWakeStrength = SimurailConfig.client().fluidVisualsWakeStrength.get().floatValue();
		values.fluidRenderRadius = SimurailConfig.client().fluidVisualsRenderRadius.get();
		values.fluidMaxCubes = SimurailConfig.client().fluidVisualsMaxCubes.get();
		values.fluidMaxTriangles = SimurailConfig.client().fluidVisualsMaxTriangles.get();
		values.fluidReplaceVanilla = SimurailConfig.client().fluidVisualsReplaceVanilla.get();
		values.fluidDebugLogging = SimurailConfig.client().fluidVisualsDebugLogging.get();
	}
	
	public static void applyClientValues(LightweightPhysicsConfigValues values) {
		SimurailConfig.client().fluidVisualsRenderStyle.set(values.fluidRenderStyle);
		SimurailConfig.client().fluidVisualsDensity.set((double) values.fluidDensity);
		SimurailConfig.client().fluidVisualsDebrisScale.set((double) values.fluidDebrisScale);
		SimurailConfig.client().fluidVisualsDebrisSpinSpeed.set((double) values.fluidDebrisSpinSpeed);
		SimurailConfig.client().fluidVisualsWaveAmplitude.set((double) values.fluidWaveAmplitude);
		SimurailConfig.client().fluidVisualsWaveLength.set((double) values.fluidWaveLength);
		SimurailConfig.client().fluidVisualsWaveSpeed.set((double) values.fluidWaveSpeed);
		SimurailConfig.client().fluidVisualsEntityInteraction.set(values.fluidEntityInteraction);
		SimurailConfig.client().fluidVisualsWakeStrength.set((double) values.fluidWakeStrength);
		SimurailConfig.client().fluidVisualsRenderRadius.set(values.fluidRenderRadius);
		SimurailConfig.client().fluidVisualsMaxCubes.set(values.fluidMaxCubes);
		SimurailConfig.client().fluidVisualsMaxTriangles.set(values.fluidMaxTriangles);
		SimurailConfig.client().fluidVisualsReplaceVanilla.set(values.fluidReplaceVanilla);
		SimurailConfig.client().fluidVisualsDebugLogging.set(values.fluidDebugLogging);
	}
}

