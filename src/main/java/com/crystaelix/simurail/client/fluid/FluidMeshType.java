package com.crystaelix.simurail.client.fluid;

/**
 * Fluid render styles available to players.
 * VANILLA uses standard Minecraft fluid rendering (no cubes/meshes).
 * Others use custom mesh rendering.
 */
public enum FluidMeshType {
	VANILLA("Vanilla (regular water)", 0, true), // Default, no custom rendering
	CUBE("Cube", 12, true), // Built-in: 2 triangles × 6 faces
	DROPLET("Droplet", 16, true), // Built-in: Low-poly water drop
	TILE("Tile", 2, true), // Built-in: Flat square
	CUSTOM("Custom", 0, false); // Server-provided custom mesh
	
	private final String displayName;
	private final int defaultTriangleCount;
	private final boolean builtIn;
	
	FluidMeshType(String displayName, int defaultTriangleCount, boolean builtIn) {
		this.displayName = displayName;
		this.defaultTriangleCount = defaultTriangleCount;
		this.builtIn = builtIn;
	}
	
	public String getDisplayName() {
		return displayName;
	}
	
	public int getDefaultTriangleCount() {
		return defaultTriangleCount;
	}
	
	public boolean isBuiltIn() {
		return builtIn;
	}
	
	public static FluidMeshType fromOrdinal(int ordinal) {
		if (ordinal < 0 || ordinal >= values().length) {
			return VANILLA;
		}
		return values()[ordinal];
	}
}

