package com.crystaelix.simurail.config;

/**
 * Client setting for how to render fluid visuals.
 */
public enum FluidRenderStyle {
	VANILLA("Vanilla (regular water)"),
	CUBE("Water Cube"),
	ICE_CUBE("Ice Cube"),
	DROPLET("Droplet"),
	TILE("Tile"),
	CUSTOM("Custom (server mesh)");
	
	private final String displayName;
	
	FluidRenderStyle(String displayName) {
		this.displayName = displayName;
	}
	
	public String getDisplayName() {
		return displayName;
	}
}
