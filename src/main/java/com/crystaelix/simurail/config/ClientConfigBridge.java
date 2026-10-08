package com.crystaelix.simurail.config;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Client config bridge using Supplier pattern instead of reflection.
 * Set by client entrypoint at FMLClientSetup to avoid classloading client config on server.
 */
public class ClientConfigBridge {
	
	private static Supplier<LightweightPhysicsConfigValues> clientValueLoader = null;
	private static Consumer<LightweightPhysicsConfigValues> clientValueApplier = null;
	
	/**
	 * Called by client entrypoint to register client config accessors.
	 * This method is never called on dedicated servers.
	 */
	public static void setClientAccessors(
			Supplier<LightweightPhysicsConfigValues> loader,
			Consumer<LightweightPhysicsConfigValues> applier) {
		clientValueLoader = loader;
		clientValueApplier = applier;
	}
	
	/**
	 * Load client config values if on client side.
	 */
	public static void loadClientValues(LightweightPhysicsConfigValues values) {
		if (clientValueLoader != null) {
			LightweightPhysicsConfigValues clientValues = clientValueLoader.get();
			values.fluidRenderStyle = clientValues.fluidRenderStyle;
			values.fluidDebrisModel = clientValues.fluidDebrisModel;
			values.fluidDensity = clientValues.fluidDensity;
			values.fluidDebrisScale = clientValues.fluidDebrisScale;
			values.fluidDebrisSpinSpeed = clientValues.fluidDebrisSpinSpeed;
			values.fluidWaveAmplitude = clientValues.fluidWaveAmplitude;
			values.fluidWaveLength = clientValues.fluidWaveLength;
			values.fluidWaveSpeed = clientValues.fluidWaveSpeed;
			values.fluidEntityInteraction = clientValues.fluidEntityInteraction;
			values.fluidWakeStrength = clientValues.fluidWakeStrength;
			values.fluidRenderRadius = clientValues.fluidRenderRadius;
			values.fluidMaxCubes = clientValues.fluidMaxCubes;
			values.fluidMaxTriangles = clientValues.fluidMaxTriangles;
			values.fluidReplaceVanilla = clientValues.fluidReplaceVanilla;
			values.fluidDebugLogging = clientValues.fluidDebugLogging;
		}
	}
	
	/**
	 * Apply client config values if on client side.
	 */
	public static void applyClientValues(LightweightPhysicsConfigValues values) {
		if (clientValueApplier != null) {
			clientValueApplier.accept(values);
		}
	}
}
