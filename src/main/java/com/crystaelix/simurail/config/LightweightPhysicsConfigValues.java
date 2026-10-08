package com.crystaelix.simurail.config;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Snapshot of lightweight physics config values for GUI editing.
 * Server fluid physics (currents/buoyancy) use fixed defaults and are not exposed in GUI.
 */
public class LightweightPhysicsConfigValues {
	// Server-side physics settings (exposed in GUI)
	public boolean enabled;
	public float activationRadius;
	public int maxActive;
	public int updateInterval;
	public float sleepVelocity;
	public boolean debugLogging;
	
	// Client-side fluid visuals (all fluid settings are client-only)
	public int fluidRenderStyle; // 0=OFF, 1=CUBE, 2=DROPLET, 3=TILE
	public float fluidDensity; // cubes per surface block
	public int fluidRenderRadius;
	public int fluidMaxCubes;
	public int fluidMaxTriangles;
	public boolean fluidReplaceVanilla;
	
	public LightweightPhysicsConfigValues() {
	}
	
	public static LightweightPhysicsConfigValues loadFromConfig() {
		LightweightPhysicsConfigValues values = new LightweightPhysicsConfigValues();
		values.enabled = SimurailConfig.server().physics.lightweightEnabled.get();
		values.activationRadius = SimurailConfig.server().physics.lightweightActivationRadius.get().floatValue();
		values.maxActive = SimurailConfig.server().physics.lightweightMaxActive.get();
		values.updateInterval = SimurailConfig.server().physics.lightweightUpdateInterval.get();
		values.sleepVelocity = SimurailConfig.server().physics.lightweightSleepVelocity.get().floatValue();
		values.debugLogging = SimurailConfig.server().physics.lightweightDebugLogging.get();
		
		// Fluid visuals (client) - only load on client side via helper to avoid classloading client config on server
		if (FMLEnvironment.dist == Dist.CLIENT) {
			loadClientConfigValues(values);
		}
		
		return values;
	}
	
	public static LightweightPhysicsConfigValues defaults() {
		LightweightPhysicsConfigValues values = new LightweightPhysicsConfigValues();
		values.enabled = true;
		values.activationRadius = 32.0f;
		values.maxActive = 256;
		values.updateInterval = 4;
		values.sleepVelocity = 0.05f;
		values.debugLogging = false;
		
		// Fluid visuals defaults (all client-side)
		values.fluidRenderStyle = 1; // CUBE (default, not OFF)
		values.fluidDensity = 0.5f; // moderate
		values.fluidRenderRadius = 16;
		values.fluidMaxCubes = 512;
		values.fluidMaxTriangles = 100000;
		values.fluidReplaceVanilla = false;
		
		return values;
	}
	
	public LightweightPhysicsConfigValues copy() {
		LightweightPhysicsConfigValues copy = new LightweightPhysicsConfigValues();
		copy.copyFrom(this);
		return copy;
	}
	
	public void copyFrom(LightweightPhysicsConfigValues other) {
		this.enabled = other.enabled;
		this.activationRadius = other.activationRadius;
		this.maxActive = other.maxActive;
		this.updateInterval = other.updateInterval;
		this.sleepVelocity = other.sleepVelocity;
		this.debugLogging = other.debugLogging;
		
		// Fluid visuals (client-only)
		this.fluidRenderStyle = other.fluidRenderStyle;
		this.fluidDensity = other.fluidDensity;
		this.fluidRenderRadius = other.fluidRenderRadius;
		this.fluidMaxCubes = other.fluidMaxCubes;
		this.fluidMaxTriangles = other.fluidMaxTriangles;
		this.fluidReplaceVanilla = other.fluidReplaceVanilla;
	}
	
	public void applyToConfig() {
		// Server-side physics settings
		SimurailConfig.server().physics.lightweightEnabled.set(enabled);
		SimurailConfig.server().physics.lightweightActivationRadius.set((double) activationRadius);
		SimurailConfig.server().physics.lightweightMaxActive.set(maxActive);
		SimurailConfig.server().physics.lightweightUpdateInterval.set(updateInterval);
		SimurailConfig.server().physics.lightweightSleepVelocity.set((double) sleepVelocity);
		SimurailConfig.server().physics.lightweightDebugLogging.set(debugLogging);
		
		// Fluid visuals (client) - only apply on client side via helper
		if (FMLEnvironment.dist == Dist.CLIENT) {
			applyClientConfigValues(this);
			// Save client config spec to disk (config/simurail-client.toml)
			SimurailConfig.client().specification.save();
		}
	}
	
	/**
	 * Helper method that delegates to client bridge (Supplier pattern, no reflection/classloading).
	 */
	private static void loadClientConfigValues(LightweightPhysicsConfigValues values) {
		ClientConfigBridge.loadClientValues(values);
	}
	
	private static void applyClientConfigValues(LightweightPhysicsConfigValues values) {
		ClientConfigBridge.applyClientValues(values);
	}
	
	public boolean validate() {
		return activationRadius >= 0 && activationRadius <= 256 &&
		       maxActive >= 0 && maxActive <= 2048 &&
		       updateInterval >= 1 && updateInterval <= 20 &&
		       sleepVelocity >= 0 && sleepVelocity <= 10 &&
		       fluidRenderStyle >= 0 && fluidRenderStyle <= 3 &&
		       fluidDensity >= 0.1f && fluidDensity <= 4.0f &&
		       fluidRenderRadius >= 4 && fluidRenderRadius <= 64 &&
		       fluidMaxCubes >= 64 && fluidMaxCubes <= 4096 &&
		       fluidMaxTriangles >= 10000 && fluidMaxTriangles <= 1000000;
	}
}
