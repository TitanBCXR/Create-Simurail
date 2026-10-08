package com.crystaelix.simurail.client.config;

/**
 * Dex5 theme color palette.
 * High-contrast dark theme with vibrant accents.
 */
public class Dex5Colors {
	// Core palette
	public static final int PRIMARY = 0xFF00BFFF;        // Deep sky blue
	public static final int SECONDARY = 0xFF9370DB;      // Medium purple
	public static final int ACCENT = 0xFFFF1493;         // Deep pink (call-to-action)
	
	// Backgrounds
	public static final int BACKGROUND = 0xFF0A0A0F;     // Very dark blue-black
	public static final int SURFACE = 0xFF1A1A2E;        // Dark blue-grey
	
	// Text
	public static final int TEXT = 0xFFFFFFFF;           // White
	public static final int TEXT_SECONDARY = 0xFFB8B8D0; // Light grey-purple
	
	// Status colors
	public static final int SUCCESS = 0xFF00FF88;        // Bright green
	public static final int WARNING = 0xFFFFB300;        // Amber
	public static final int ERROR = 0xFFFF4444;          // Bright red
	
	// Derived colors for UI elements
	public static final int BORDER_ACCENT = 0x80FF1493;  // Semi-transparent accent
	public static final int HOVER_OVERLAY = 0x20FFFFFF;  // Subtle white overlay
	public static final int DISABLED_OVERLAY = 0x80000000; // Dark semi-transparent
	
	// Toggle states
	public static final int TOGGLE_ON = SUCCESS;
	public static final int TOGGLE_OFF = 0xFF444444;     // Dark grey
	public static final int TOGGLE_DISABLED = 0xFF222222; // Very dark grey
}
