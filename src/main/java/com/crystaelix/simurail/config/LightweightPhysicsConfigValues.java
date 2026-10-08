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
	public int fluidMaxMeshTriangles;
	
	// Fluid visuals (client-side)
	public int fluidRenderStyle; // 0=VANILLA, 1=CUBE, 2=DROPLET, 3=TILE, 4=CUSTOM
	public int fluidCubesPerBlock;
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
		
		// Fluid physics (server)
		values.fluidsEnabled = SimurailConfig.server().physics.fluidsCurrentsEnabled.get();
		values.fluidCurrentStrength = SimurailConfig.server().physics.fluidsCurrentStrength.get().floatValue();
		values.fluidBuoyancy = SimurailConfig.server().physics.fluidsBuoyancy.get().floatValue();
		values.fluidMaxMeshTriangles = SimurailConfig.server().physics.fluidsMaxMeshTriangles.get();
		
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
		values.fluidMaxMeshTriangles = 48;
		
		// Fluid visuals defaults
		values.fluidRenderStyle = 0; // VANILLA
		values.fluidCubesPerBlock = 8;
		values.fluidRenderRadius = 16;
		values.fluidMaxCubes = 4096;
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
		
		// Fluid physics
		this.fluidsEnabled = other.fluidsEnabled;
		this.fluidCurrentStrength = other.fluidCurrentStrength;
		this.fluidBuoyancy = other.fluidBuoyancy;
		this.fluidMaxMeshTriangles = other.fluidMaxMeshTriangles;
		
		// Fluid visuals
		this.fluidRenderStyle = other.fluidRenderStyle;
		this.fluidCubesPerBlock = other.fluidCubesPerBlock;
		this.fluidRenderRadius = other.fluidRenderRadius;
		this.fluidMaxCubes = other.fluidMaxCubes;
		this.fluidMaxTriangles = other.fluidMaxTriangles;
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
		SimurailConfig.server().physics.fluidsMaxMeshTriangles.set(fluidMaxMeshTriangles);
		
		// Fluid visuals (client) - only apply on client side via helper
		if (FMLEnvironment.dist == Dist.CLIENT) {
			applyClientConfigValues(this);
		}
		
		// Note: Config auto-saves in NeoForge, no manual save needed
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
		       fluidCurrentStrength >= 0 && fluidCurrentStrength <= 1 &&
		       fluidBuoyancy >= 0 && fluidBuoyancy <= 1 &&
		       fluidMaxMeshTriangles >= 2 && fluidMaxMeshTriangles <= 256 &&
		       fluidRenderStyle >= 0 && fluidRenderStyle <= 4 &&
		       (fluidCubesPerBlock == 1 || fluidCubesPerBlock == 8 || fluidCubesPerBlock == 27) &&
		       fluidRenderRadius >= 4 && fluidRenderRadius <= 64 &&
		       fluidMaxCubes >= 256 && fluidMaxCubes <= 32768 &&
		       fluidMaxTriangles >= 10000 && fluidMaxTriangles <= 1000000;
	}
}
