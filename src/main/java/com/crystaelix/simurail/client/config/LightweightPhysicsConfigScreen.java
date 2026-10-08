package com.crystaelix.simurail.client.config;

import com.crystaelix.simurail.Simurail;
import com.crystaelix.simurail.network.SimurailPackets;
import com.crystaelix.simurail.network.UpdateLightweightPhysicsConfigPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Custom config screen for Lightweight Physics settings.
 * Styled with Dex5 theme (high-contrast dark with vibrant accents).
 * Features transparent background and optional branding watermark.
 */
public class LightweightPhysicsConfigScreen extends Screen {
	
	private static final ResourceLocation WATERMARK_TEXTURE = 
		Simurail.id("textures/gui/titan_logo_watermark.png");
	private static final float WATERMARK_ALPHA = 0.12f;
	private static final boolean HAS_BRANDING = checkBranding();
	
	private final Screen parent;
	private final LightweightPhysicsConfigValues originalValues;
	private final LightweightPhysicsConfigValues currentValues;
	private final boolean canEdit;
	
	// UI state
	private boolean isDirty = false;
	private String statusMessage = "";
	private int statusColor = Dex5Colors.TEXT_SECONDARY;
	private long statusTime = 0;
	
	// Layout constants (proportional sizing)
	private static final float PANEL_WIDTH_RATIO = 0.85f;  // 85% of screen width
	private static final int PANEL_MIN_WIDTH = 360;
	private static final int PANEL_MAX_WIDTH = 480;
	private static final float PANEL_HEIGHT_RATIO = 0.90f; // 90% of screen height
	private static final int PANEL_MIN_HEIGHT = 400;
	
	private static final int TITLE_OFFSET = 20;
	private static final int CONTENT_START_OFFSET = 50;
	private static final int WIDGET_HEIGHT = 24;
	private static final int WIDGET_SPACING = 6;
	private static final int BIG_TOGGLE_HEIGHT = 40;
	private static final int BUTTON_WIDTH = 100;
	private static final int BUTTON_HEIGHT = 24;
	private static final int BOTTOM_BUTTON_MARGIN = 35;
	
	// Computed layout values
	private int panelX, panelY, panelWidth, panelHeight;
	private int contentWidth;
	
	// Slider components
	private Dex5Slider radiusSlider;
	private Dex5Slider maxActiveSlider;
	private Dex5Slider updateIntervalSlider;
	private Dex5Slider sleepVelocitySlider;
	
	// Toggle buttons
	private Dex5ToggleButton enabledToggle;
	private Dex5ToggleButton debugToggle;
	
	// Action buttons
	private Button resetButton;
	private Button saveButton;
	private Button cancelButton;
	
	public LightweightPhysicsConfigScreen(Screen parent) {
		super(Component.literal("Lightweight Physics Configuration"));
		this.parent = parent;
		this.originalValues = LightweightPhysicsConfigValues.loadFromConfig();
		this.currentValues = originalValues.copy();
		
		// Check if player can edit (ops in multiplayer, always yes in singleplayer)
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.level != null) {
			this.canEdit = mc.hasSingleplayerServer() || mc.player.hasPermissions(2);
		} else {
			this.canEdit = false;
		}
	}
	
	@Override
	protected void init() {
		super.init();
		
		// Compute responsive layout
		panelWidth = Mth.clamp((int)(this.width * PANEL_WIDTH_RATIO), PANEL_MIN_WIDTH, PANEL_MAX_WIDTH);
		panelWidth = Math.min(panelWidth, this.width - 20);
		
		panelHeight = (int)(this.height * PANEL_HEIGHT_RATIO);
		panelHeight = Math.max(panelHeight, PANEL_MIN_HEIGHT);
		panelHeight = Math.min(panelHeight, this.height - 20);
		
		panelX = (this.width - panelWidth) / 2;
		panelY = (this.height - panelHeight) / 2;
		
		contentWidth = panelWidth - 40; // 20px margin on each side
		
		int centerX = this.width / 2;
		int leftX = panelX + 20;
		int y = panelY + CONTENT_START_OFFSET;
		
		// Big enabled toggle at top
		enabledToggle = new Dex5ToggleButton(
			centerX - 100, y, 200, 40,
			Component.literal("Lightweight Physics"),
			currentValues.enabled,
			button -> {
				currentValues.enabled = !currentValues.enabled;
				((Dex5ToggleButton) button).setState(currentValues.enabled);
				markDirty();
			}
		);
		enabledToggle.active = canEdit;
		addRenderableWidget(enabledToggle);
		y += 50;
		
		// Activation radius slider
		radiusSlider = new Dex5Slider(
			leftX, y, contentWidth, WIDGET_HEIGHT,
			Component.literal("Activation Radius: "),
			Component.literal("m"),
			0, 256, currentValues.activationRadius,
			value -> {
				currentValues.activationRadius = value.floatValue();
				markDirty();
			}
		);
		radiusSlider.active = canEdit;
		addRenderableWidget(radiusSlider);
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		// Max active slider
		maxActiveSlider = new Dex5Slider(
			leftX, y, contentWidth, WIDGET_HEIGHT,
			Component.literal("Max Active Objects: "),
			Component.literal(""),
			0, 2048, currentValues.maxActive,
			value -> {
				currentValues.maxActive = value.intValue();
				markDirty();
			}
		);
		maxActiveSlider.active = canEdit;
		addRenderableWidget(maxActiveSlider);
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		// Update interval slider
		updateIntervalSlider = new Dex5Slider(
			leftX, y, contentWidth, WIDGET_HEIGHT,
			Component.literal("Update Interval: "),
			Component.literal(" ticks"),
			1, 20, currentValues.updateInterval,
			value -> {
				currentValues.updateInterval = value.intValue();
				markDirty();
			}
		);
		updateIntervalSlider.active = canEdit;
		addRenderableWidget(updateIntervalSlider);
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		// Sleep velocity slider
		sleepVelocitySlider = new Dex5Slider(
			leftX, y, contentWidth, WIDGET_HEIGHT,
			Component.literal("Sleep Velocity: "),
			Component.literal(" m/s"),
			0, 10, currentValues.sleepVelocity,
			value -> {
				currentValues.sleepVelocity = value.floatValue();
				markDirty();
			}
		);
		sleepVelocitySlider.active = canEdit;
		addRenderableWidget(sleepVelocitySlider);
		y += WIDGET_HEIGHT + WIDGET_SPACING + 10;
		
		// Debug logging toggle
		debugToggle = new Dex5ToggleButton(
			centerX - 80, y, 160, 24,
			Component.literal("Debug Logging"),
			currentValues.debugLogging,
			button -> {
				currentValues.debugLogging = !currentValues.debugLogging;
				((Dex5ToggleButton) button).setState(currentValues.debugLogging);
				markDirty();
			}
		);
		debugToggle.active = canEdit;
		addRenderableWidget(debugToggle);
		y += 40;
		
		// Action buttons at bottom
		int buttonY = panelY + panelHeight - BOTTOM_BUTTON_MARGIN;
		
		resetButton = Button.builder(
			Component.literal("Reset"),
			button -> resetToDefaults()
		)
		.bounds(centerX - BUTTON_WIDTH - 110, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
		.build();
		resetButton.active = canEdit;
		addRenderableWidget(resetButton);
		
		saveButton = Button.builder(
			Component.literal("Save"),
			button -> saveChanges()
		)
		.bounds(centerX - BUTTON_WIDTH / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
		.build();
		saveButton.active = canEdit && isDirty;
		addRenderableWidget(saveButton);
		
		cancelButton = Button.builder(
			Component.literal("Cancel"),
			button -> onClose()
		)
		.bounds(centerX + 10, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
		.build();
		addRenderableWidget(cancelButton);
		
		// Show permission message if read-only
		if (!canEdit) {
			setStatusMessage("Read-only: Requires operator permissions", Dex5Colors.WARNING);
		}
	}
	
	private void markDirty() {
		isDirty = true;
		if (saveButton != null) {
			saveButton.active = canEdit;
		}
	}
	
	private void resetToDefaults() {
		LightweightPhysicsConfigValues defaults = LightweightPhysicsConfigValues.defaults();
		currentValues.copyFrom(defaults);
		refreshWidgets();
		markDirty();
		setStatusMessage("Reset to defaults", Dex5Colors.TEXT_SECONDARY);
	}
	
	private void saveChanges() {
		if (!canEdit) {
			setStatusMessage("Error: No permission to save", Dex5Colors.ERROR);
			return;
		}
		
		// Send packet to server
		UpdateLightweightPhysicsConfigPacket packet = new UpdateLightweightPhysicsConfigPacket(currentValues);
		SimurailPackets.sendToServer(packet);
		
		// Update local copy
		originalValues.copyFrom(currentValues);
		isDirty = false;
		if (saveButton != null) {
			saveButton.active = false;
		}
		
		setStatusMessage("Saved successfully", Dex5Colors.SUCCESS);
	}
	
	private void refreshWidgets() {
		if (enabledToggle != null) enabledToggle.setState(currentValues.enabled);
		if (debugToggle != null) debugToggle.setState(currentValues.debugLogging);
		if (radiusSlider != null) radiusSlider.setValue(currentValues.activationRadius);
		if (maxActiveSlider != null) maxActiveSlider.setValue(currentValues.maxActive);
		if (updateIntervalSlider != null) updateIntervalSlider.setValue(currentValues.updateInterval);
		if (sleepVelocitySlider != null) sleepVelocitySlider.setValue(currentValues.sleepVelocity);
	}
	
	private void setStatusMessage(String message, int color) {
		this.statusMessage = message;
		this.statusColor = color;
		this.statusTime = System.currentTimeMillis();
	}
	
	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Render transparent background first
		renderBackground(graphics, mouseX, mouseY, partialTick);
		
		// Semi-transparent surface (80% alpha)
		int surfaceColor = (0xCC << 24) | (Dex5Colors.SURFACE & 0x00FFFFFF);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, surfaceColor);
		
		// Watermark (if branding enabled and texture exists)
		if (HAS_BRANDING) {
			renderWatermark(graphics);
		}
		
		// Accent border
		drawBorder(graphics, panelX, panelY, panelWidth, panelHeight, Dex5Colors.BORDER_ACCENT);
		
		// Title
		int titleY = panelY + TITLE_OFFSET;
		graphics.drawCenteredString(this.font, this.title, this.width / 2, titleY, Dex5Colors.PRIMARY);
		
		// Subtitle
		String subtitle = canEdit ? "Configure Lightweight Physics Settings" : "View Configuration (Read-Only)";
		graphics.drawCenteredString(this.font, subtitle, this.width / 2, titleY + 12, Dex5Colors.TEXT_SECONDARY);
		
		// Render widgets
		super.render(graphics, mouseX, mouseY, partialTick);
		
		// Status message (fade after 3 seconds)
		if (!statusMessage.isEmpty()) {
			long elapsed = System.currentTimeMillis() - statusTime;
			if (elapsed < 3000) {
				int alpha = elapsed < 2500 ? 255 : (int) (255 * (1 - (elapsed - 2500) / 500.0));
				int color = (alpha << 24) | (statusColor & 0x00FFFFFF);
				int statusY = panelY + panelHeight - BOTTOM_BUTTON_MARGIN - 20;
				graphics.drawCenteredString(this.font, statusMessage, this.width / 2, statusY, color);
			}
		}
	}
	
	private void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
		graphics.fill(x - 1, y - 1, x + width + 1, y, color); // Top
		graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, color); // Bottom
		graphics.fill(x - 1, y, x, y + height, color); // Left
		graphics.fill(x + width, y, x + width + 1, y + height, color); // Right
	}
	
	@Override
	public void onClose() {
		if (isDirty && canEdit) {
			// Restore original values
			currentValues.copyFrom(originalValues);
		}
		minecraft.setScreen(parent);
	}
	
	@Override
	public boolean isPauseScreen() {
		return false;
	}
	
	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Override to prevent default background rendering
		// Light dim overlay (20% black)
		graphics.fill(0, 0, this.width, this.height, 0x33000000);
	}
	
	private void renderWatermark(GuiGraphics graphics) {
		try {
			// Calculate watermark size to fit panel while maintaining aspect ratio
			// Logo is square (1024x1024), scale to 50% of panel's smaller dimension
			int maxSize = (int)(Math.min(panelWidth, panelHeight) * 0.5);
			int watermarkSize = Math.min(maxSize, 256); // Cap at 256px
			
			// Center in panel
			int x = panelX + (panelWidth - watermarkSize) / 2;
			int y = panelY + (panelHeight - watermarkSize) / 2;
			
			// Render with low alpha
			graphics.setColor(1.0f, 1.0f, 1.0f, WATERMARK_ALPHA);
			
			graphics.blit(
				WATERMARK_TEXTURE,
				x, y,
				0, 0,
				watermarkSize, watermarkSize,
				watermarkSize, watermarkSize
			);
			
			// Reset color
			graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
		} catch (Exception e) {
			// Silently ignore if texture not found
		}
	}
	
	private static boolean checkBranding() {
		// Check if titan branding texture exists in jar
		try {
			Minecraft.getInstance().getResourceManager()
				.getResource(WATERMARK_TEXTURE);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
