package com.crystaelix.simurail.config;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Snapshot of lightweight physics config values for GUI editing.
 */
public class LightweightPhysicsConfigValues {
	public boolean enabled;
	public float activationRadius;
	public int maxActive;
	public int updateInterval;
	public float sleepVelocity;
	public boolean debugLogging;
	
	// Fluid physics (server-side)
	public boolean fluidsEnabled;
	public float fluidCurrentStrength;
	public float fluidBuoyancy;
	
	// Fluid visuals (client-side)
	public boolean fluidVisualsEnabled;
	public int fluidCubesPerBlock;
	public int fluidRenderRadius;
	public int fluidMaxCubes;
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
		
		// Fluid physics (server)
		values.fluidsEnabled = SimurailConfig.server().physics.fluidsCurrentsEnabled.get();
		values.fluidCurrentStrength = SimurailConfig.server().physics.fluidsCurrentStrength.get().floatValue();
		values.fluidBuoyancy = SimurailConfig.server().physics.fluidsBuoyancy.get().floatValue();
		
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
		
		// Fluid physics defaults
		values.fluidsEnabled = true;
		values.fluidCurrentStrength = 0.04f;
		values.fluidBuoyancy = 0.03f;
		
		// Fluid visuals defaults
		values.fluidVisualsEnabled = false;
		values.fluidCubesPerBlock = 8;
		values.fluidRenderRadius = 16;
		values.fluidMaxCubes = 4096;
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
		
		// Fluid physics
		this.fluidsEnabled = other.fluidsEnabled;
		this.fluidCurrentStrength = other.fluidCurrentStrength;
		this.fluidBuoyancy = other.fluidBuoyancy;
		
		// Fluid visuals
		this.fluidVisualsEnabled = other.fluidVisualsEnabled;
		this.fluidCubesPerBlock = other.fluidCubesPerBlock;
		this.fluidRenderRadius = other.fluidRenderRadius;
		this.fluidMaxCubes = other.fluidMaxCubes;
		this.fluidReplaceVanilla = other.fluidReplaceVanilla;
	}
	
	public void applyToConfig() {
		SimurailConfig.server().physics.lightweightEnabled.set(enabled);
		SimurailConfig.server().physics.lightweightActivationRadius.set((double) activationRadius);
		SimurailConfig.server().physics.lightweightMaxActive.set(maxActive);
		SimurailConfig.server().physics.lightweightUpdateInterval.set(updateInterval);
		SimurailConfig.server().physics.lightweightSleepVelocity.set((double) sleepVelocity);
		SimurailConfig.server().physics.lightweightDebugLogging.set(debugLogging);
		
		// Fluid physics (server)
		SimurailConfig.server().physics.fluidsCurrentsEnabled.set(fluidsEnabled);
		SimurailConfig.server().physics.fluidsCurrentStrength.set((double) fluidCurrentStrength);
		SimurailConfig.server().physics.fluidsBuoyancy.set((double) fluidBuoyancy);
		
		// Fluid visuals (client) - only apply on client side via helper
		if (FMLEnvironment.dist == Dist.CLIENT) {
			applyClientConfigValues(this);
		}
		
		// Note: Config auto-saves in NeoForge, no manual save needed
	}
	
	/**
	 * Helper method that delegates to client-only class to avoid classloading client config on server.
	 * The actual implementation is in ClientConfigHelper which is annotated @OnlyIn(Dist.CLIENT).
	 */
	private static void loadClientConfigValues(LightweightPhysicsConfigValues values) {
		// This method body only executes on client, reflection ensures class isn't loaded on server
		try {
			Class<?> helperClass = Class.forName("com.crystaelix.simurail.client.config.ClientConfigHelper");
			helperClass.getMethod("loadClientValues", LightweightPhysicsConfigValues.class).invoke(null, values);
		} catch (Exception e) {
			// Ignore on server
		}
	}
	
	private static void applyClientConfigValues(LightweightPhysicsConfigValues values) {
		try {
			Class<?> helperClass = Class.forName("com.crystaelix.simurail.client.config.ClientConfigHelper");
			helperClass.getMethod("applyClientValues", LightweightPhysicsConfigValues.class).invoke(null, values);
		} catch (Exception e) {
			// Ignore on server
		}
	}
	
	public boolean validate() {
		return activationRadius >= 0 && activationRadius <= 256 &&
		       maxActive >= 0 && maxActive <= 2048 &&
		       updateInterval >= 1 && updateInterval <= 20 &&
		       sleepVelocity >= 0 && sleepVelocity <= 10 &&
		       fluidCurrentStrength >= 0 && fluidCurrentStrength <= 1 &&
		       fluidBuoyancy >= 0 && fluidBuoyancy <= 1 &&
		       (fluidCubesPerBlock == 1 || fluidCubesPerBlock == 8 || fluidCubesPerBlock == 27) &&
		       fluidRenderRadius >= 4 && fluidRenderRadius <= 64 &&
		       fluidMaxCubes >= 256 && fluidMaxCubes <= 32768;
	}
}
