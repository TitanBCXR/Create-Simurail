package com.crystaelix.simurail.client.config;

import com.crystaelix.simurail.config.SimurailConfig;

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
	}
	
	public void applyToConfig() {
		SimurailConfig.server().physics.lightweightEnabled.set(enabled);
		SimurailConfig.server().physics.lightweightActivationRadius.set((double) activationRadius);
		SimurailConfig.server().physics.lightweightMaxActive.set(maxActive);
		SimurailConfig.server().physics.lightweightUpdateInterval.set(updateInterval);
		SimurailConfig.server().physics.lightweightSleepVelocity.set((double) sleepVelocity);
		SimurailConfig.server().physics.lightweightDebugLogging.set(debugLogging);
		// Note: Config auto-saves in NeoForge, no manual save needed
	}
	
	public boolean validate() {
		return activationRadius >= 0 && activationRadius <= 256 &&
		       maxActive >= 0 && maxActive <= 2048 &&
		       updateInterval >= 1 && updateInterval <= 20 &&
		       sleepVelocity >= 0 && sleepVelocity <= 10;
	}
}
