package com.crystaelix.simurail.client.config;

import com.crystaelix.simurail.Simurail;
import com.crystaelix.simurail.config.LightweightPhysicsConfigValues;
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
 * Implements scrollable content area with scissor clipping.
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
	private static final float PANEL_WIDTH_RATIO = 0.85f;
	private static final int PANEL_MIN_WIDTH = 360;
	private static final int PANEL_MAX_WIDTH = 520;
	private static final float PANEL_HEIGHT_RATIO = 0.90f;
	private static final int PANEL_MIN_HEIGHT = 400;
	
	private static final int TITLE_HEIGHT = 40;
	private static final int FOOTER_HEIGHT = 50;
	private static final int SCROLL_MARGIN = 20;
	private static final int WIDGET_HEIGHT = 24;
	private static final int WIDGET_SPACING = 6;
	private static final int BIG_TOGGLE_HEIGHT = 40;
	private static final int SECTION_HEADER_HEIGHT = 20;
	private static final int SECTION_SPACING = 10;
	private static final int BUTTON_WIDTH = 100;
	private static final int BUTTON_HEIGHT = 24;
	
	// Computed layout values
	private int panelX, panelY, panelWidth, panelHeight;
	private int contentX, contentY, contentWidth, contentHeight;
	private double scrollOffset = 0;
	private double maxScrollOffset = 0;
	
	// Slider components
	private Dex5Slider radiusSlider;
	private Dex5Slider maxActiveSlider;
	private Dex5Slider updateIntervalSlider;
	private Dex5Slider sleepVelocitySlider;
	private Dex5Slider fluidCurrentStrengthSlider;
	private Dex5Slider fluidBuoyancySlider;
	private Dex5Slider fluidRenderRadiusSlider;
	private Dex5Slider fluidMaxCubesSlider;
	private Dex5Slider cubesPerBlockSlider;
	
	// Toggle buttons
	private Dex5ToggleButton enabledToggle;
	private Dex5ToggleButton debugToggle;
	private Dex5ToggleButton fluidsToggle;
	private Dex5ToggleButton fluidVisualsToggle;
	private Dex5ToggleButton fluidReplaceVanillaToggle;
	
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
		
		// Content area (scrollable region)
		contentX = panelX + SCROLL_MARGIN;
		contentY = panelY + TITLE_HEIGHT;
		contentWidth = panelWidth - SCROLL_MARGIN * 2;
		contentHeight = panelHeight - TITLE_HEIGHT - FOOTER_HEIGHT;
		
		// Build widgets (not added yet, will be positioned during render)
		buildWidgets();
		
		// Calculate max scroll based on total content height
		int totalContentHeight = calculateTotalContentHeight();
		maxScrollOffset = Math.max(0, totalContentHeight - contentHeight);
		
		// Add footer buttons (fixed position, always visible)
		addFooterButtons();
		
		// Show permission message if read-only
		if (!canEdit) {
			setStatusMessage("Read-only: Requires operator permissions", Dex5Colors.WARNING);
		}
	}
	
	private void buildWidgets() {
		int widgetWidth = contentWidth - 40;
		int leftX = contentX + 20;
		
		// Big enabled toggle
		enabledToggle = new Dex5ToggleButton(
			leftX + widgetWidth / 2 - 100, 0, 200, BIG_TOGGLE_HEIGHT,
			Component.literal("Lightweight Physics"),
			currentValues.enabled,
			button -> {
				currentValues.enabled = !currentValues.enabled;
				((Dex5ToggleButton) button).setState(currentValues.enabled);
				markDirty();
			}
		);
		enabledToggle.active = canEdit;
		
		// Physics section sliders
		radiusSlider = new Dex5Slider(
			leftX, 0, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Activation Radius: "),
			Component.literal(" m"),
			0, 256, currentValues.activationRadius,
			value -> {
				currentValues.activationRadius = value.floatValue();
				markDirty();
			}
		);
		radiusSlider.active = canEdit;
		
		maxActiveSlider = new Dex5Slider(
			leftX, 0, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Max Active Objects: "),
			Component.literal(""),
			0, 2048, currentValues.maxActive,
			value -> {
				currentValues.maxActive = value.intValue();
				markDirty();
			}
		);
		maxActiveSlider.active = canEdit;
		
		updateIntervalSlider = new Dex5Slider(
			leftX, 0, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Update Interval: "),
			Component.literal(" ticks"),
			1, 20, currentValues.updateInterval,
			value -> {
				currentValues.updateInterval = value.intValue();
				markDirty();
			}
		);
		updateIntervalSlider.active = canEdit;
		
		sleepVelocitySlider = new Dex5Slider(
			leftX, 0, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Sleep Velocity: "),
			Component.literal(" m/s"),
			0, 10, currentValues.sleepVelocity,
			value -> {
				currentValues.sleepVelocity = value.floatValue();
				markDirty();
			},
			true // Force decimal formatting
		);
		sleepVelocitySlider.active = canEdit;
		
		debugToggle = new Dex5ToggleButton(
			leftX + widgetWidth / 2 - 80, 0, 160, WIDGET_HEIGHT,
			Component.literal("Debug Logging"),
			currentValues.debugLogging,
			button -> {
				currentValues.debugLogging = !currentValues.debugLogging;
				((Dex5ToggleButton) button).setState(currentValues.debugLogging);
				markDirty();
			}
		);
		debugToggle.active = canEdit;
		
		// Fluid server section
		fluidsToggle = new Dex5ToggleButton(
			leftX + widgetWidth / 2 - 100, 0, 200, 30,
			Component.literal("Fluid Currents"),
			currentValues.fluidsEnabled,
			button -> {
				currentValues.fluidsEnabled = !currentValues.fluidsEnabled;
				((Dex5ToggleButton) button).setState(currentValues.fluidsEnabled);
				markDirty();
			}
		);
		fluidsToggle.active = canEdit;
		
		fluidCurrentStrengthSlider = new Dex5Slider(
			leftX, 0, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Current Strength: "),
			Component.literal(""),
			0, 1, currentValues.fluidCurrentStrength,
			value -> {
				currentValues.fluidCurrentStrength = value.floatValue();
				markDirty();
			}
		);
		fluidCurrentStrengthSlider.active = canEdit;
		
		fluidBuoyancySlider = new Dex5Slider(
			leftX, 0, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Buoyancy: "),
			Component.literal(""),
			0, 1, currentValues.fluidBuoyancy,
			value -> {
				currentValues.fluidBuoyancy = value.floatValue();
				markDirty();
			}
		);
		fluidBuoyancySlider.active = canEdit;
		
		// Fluid client section
		fluidVisualsToggle = new Dex5ToggleButton(
			leftX + widgetWidth / 2 - 90, 0, 180, 30,
			Component.literal("Flowing Cubes"),
			currentValues.fluidRenderStyle > 0,
			button -> {
				currentValues.fluidRenderStyle = (currentValues.fluidRenderStyle == 0) ? 1 : 0;
				((Dex5ToggleButton) button).setState(currentValues.fluidRenderStyle > 0);
				markDirty();
			}
		);
		fluidVisualsToggle.active = true;
		
		cubesPerBlockSlider = new Dex5Slider(
			leftX, 0, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Cubes Per Block: "),
			Component.literal(""),
			1, 27, currentValues.fluidCubesPerBlock,
			value -> {
				int v = value.intValue();
				if (v <= 4) {
					currentValues.fluidCubesPerBlock = 1;
				} else if (v <= 17) {
					currentValues.fluidCubesPerBlock = 8;
				} else {
					currentValues.fluidCubesPerBlock = 27;
				}
				markDirty();
			}
		);
		cubesPerBlockSlider.active = true;
		
		fluidRenderRadiusSlider = new Dex5Slider(
			leftX, 0, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Render Radius: "),
			Component.literal(" blocks"),
			4, 64, currentValues.fluidRenderRadius,
			value -> {
				currentValues.fluidRenderRadius = value.intValue();
				markDirty();
			}
		);
		fluidRenderRadiusSlider.active = true;
		
		fluidMaxCubesSlider = new Dex5Slider(
			leftX, 0, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Max Cubes: "),
			Component.literal(""),
			256, 32768, currentValues.fluidMaxCubes,
			value -> {
				currentValues.fluidMaxCubes = value.intValue();
				markDirty();
			}
		);
		fluidMaxCubesSlider.active = true;
		
		fluidReplaceVanillaToggle = new Dex5ToggleButton(
			leftX + widgetWidth / 2 - 90, 0, 180, WIDGET_HEIGHT,
			Component.literal("Replace Vanilla Fluid"),
			currentValues.fluidReplaceVanilla,
			button -> {
				currentValues.fluidReplaceVanilla = !currentValues.fluidReplaceVanilla;
				((Dex5ToggleButton) button).setState(currentValues.fluidReplaceVanilla);
				markDirty();
			}
		);
		fluidReplaceVanillaToggle.active = true;
	}
	
	private int calculateTotalContentHeight() {
		int height = 0;
		
		// Main toggle
		height += BIG_TOGGLE_HEIGHT + SECTION_SPACING;
		
		// Physics section
		height += SECTION_HEADER_HEIGHT + SECTION_SPACING;
		height += (WIDGET_HEIGHT + WIDGET_SPACING) * 4; // 4 sliders
		height += SECTION_SPACING;
		
		// Debug section
		height += SECTION_HEADER_HEIGHT + SECTION_SPACING;
		height += WIDGET_HEIGHT + SECTION_SPACING;
		
		// Fluids (Server) section
		height += SECTION_HEADER_HEIGHT + SECTION_SPACING;
		height += 30 + WIDGET_SPACING; // Toggle
		height += (WIDGET_HEIGHT + WIDGET_SPACING) * 2; // 2 sliders
		height += SECTION_SPACING;
		
		// Fluid Visuals (Client) section
		height += SECTION_HEADER_HEIGHT + SECTION_SPACING;
		height += 30 + WIDGET_SPACING; // Toggle
		height += (WIDGET_HEIGHT + WIDGET_SPACING) * 4; // 4 sliders/widgets
		height += WIDGET_HEIGHT + SECTION_SPACING; // Replace vanilla toggle
		
		return height;
	}
	
	private void addFooterButtons() {
		int buttonY = panelY + panelHeight - FOOTER_HEIGHT + (FOOTER_HEIGHT - BUTTON_HEIGHT) / 2;
		int centerX = this.width / 2;
		int buttonSpacing = 10;
		int totalWidth = BUTTON_WIDTH * 3 + buttonSpacing * 2;
		int startX = centerX - totalWidth / 2;
		
		resetButton = Button.builder(
			Component.literal("Reset"),
			button -> resetToDefaults()
		)
		.bounds(startX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
		.build();
		resetButton.active = canEdit;
		addRenderableWidget(resetButton);
		
		saveButton = Button.builder(
			Component.literal("Save"),
			button -> saveChanges()
		)
		.bounds(startX + BUTTON_WIDTH + buttonSpacing, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
		.build();
		saveButton.active = canEdit && isDirty;
		addRenderableWidget(saveButton);
		
		cancelButton = Button.builder(
			Component.literal("Cancel"),
			button -> onClose()
		)
		.bounds(startX + (BUTTON_WIDTH + buttonSpacing) * 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
		.build();
		addRenderableWidget(cancelButton);
	}
	
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= contentX && mouseX <= contentX + contentWidth &&
		    mouseY >= contentY && mouseY <= contentY + contentHeight) {
			scrollOffset = Mth.clamp(scrollOffset - scrollY * 20, 0, maxScrollOffset);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}
	
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// Check if click is within scrollable content area
		if (mouseX >= contentX && mouseX <= contentX + contentWidth &&
		    mouseY >= contentY && mouseY <= contentY + contentHeight) {
			
			// Adjust mouse coordinates for scroll offset
			double adjustedY = mouseY + scrollOffset;
			
			// Check each widget
			if (checkWidgetClick(enabledToggle, mouseX, adjustedY, button)) return true;
			if (checkWidgetClick(radiusSlider, mouseX, adjustedY, button)) return true;
			if (checkWidgetClick(maxActiveSlider, mouseX, adjustedY, button)) return true;
			if (checkWidgetClick(updateIntervalSlider, mouseX, adjustedY, button)) return true;
			if (checkWidgetClick(sleepVelocitySlider, mouseX, adjustedY, button)) return true;
			if (checkWidgetClick(debugToggle, mouseX, adjustedY, button)) return true;
			if (checkWidgetClick(fluidsToggle, mouseX, adjustedY, button)) return true;
			if (checkWidgetClick(fluidCurrentStrengthSlider, mouseX, adjustedY, button)) return true;
			if (checkWidgetClick(fluidBuoyancySlider, mouseX, adjustedY, button)) return true;
			if (checkWidgetClick(fluidVisualsToggle, mouseX, adjustedY, button)) return true;
			if (checkWidgetClick(cubesPerBlockSlider, mouseX, adjustedY, button)) return true;
			if (checkWidgetClick(fluidRenderRadiusSlider, mouseX, adjustedY, button)) return true;
			if (checkWidgetClick(fluidMaxCubesSlider, mouseX, adjustedY, button)) return true;
			if (checkWidgetClick(fluidReplaceVanillaToggle, mouseX, adjustedY, button)) return true;
		}
		
		return super.mouseClicked(mouseX, mouseY, button);
	}
	
	private boolean checkWidgetClick(net.minecraft.client.gui.components.AbstractWidget widget, 
	                                  double mouseX, double mouseY, int button) {
		if (widget != null && widget.active && 
		    mouseX >= widget.getX() && mouseX <= widget.getX() + widget.getWidth() &&
		    mouseY >= widget.getY() && mouseY <= widget.getY() + widget.getHeight()) {
			widget.onClick(mouseX, mouseY);
			return true;
		}
		return false;
	}
	
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		// Check if drag is within scrollable content area
		if (mouseX >= contentX && mouseX <= contentX + contentWidth &&
		    mouseY >= contentY && mouseY <= contentY + contentHeight) {
			
			double adjustedY = mouseY + scrollOffset;
			
			// Check sliders for dragging
			if (radiusSlider != null && radiusSlider.active) radiusSlider.mouseDragged(mouseX, adjustedY, button, dragX, dragY);
			if (maxActiveSlider != null && maxActiveSlider.active) maxActiveSlider.mouseDragged(mouseX, adjustedY, button, dragX, dragY);
			if (updateIntervalSlider != null && updateIntervalSlider.active) updateIntervalSlider.mouseDragged(mouseX, adjustedY, button, dragX, dragY);
			if (sleepVelocitySlider != null && sleepVelocitySlider.active) sleepVelocitySlider.mouseDragged(mouseX, adjustedY, button, dragX, dragY);
			if (fluidCurrentStrengthSlider != null && fluidCurrentStrengthSlider.active) fluidCurrentStrengthSlider.mouseDragged(mouseX, adjustedY, button, dragX, dragY);
			if (fluidBuoyancySlider != null && fluidBuoyancySlider.active) fluidBuoyancySlider.mouseDragged(mouseX, adjustedY, button, dragX, dragY);
			if (cubesPerBlockSlider != null && cubesPerBlockSlider.active) cubesPerBlockSlider.mouseDragged(mouseX, adjustedY, button, dragX, dragY);
			if (fluidRenderRadiusSlider != null && fluidRenderRadiusSlider.active) fluidRenderRadiusSlider.mouseDragged(mouseX, adjustedY, button, dragX, dragY);
			if (fluidMaxCubesSlider != null && fluidMaxCubesSlider.active) fluidMaxCubesSlider.mouseDragged(mouseX, adjustedY, button, dragX, dragY);
		}
		
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
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
		
		UpdateLightweightPhysicsConfigPacket packet = new UpdateLightweightPhysicsConfigPacket(currentValues);
		SimurailPackets.sendToServer(packet);
		
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
		if (fluidsToggle != null) fluidsToggle.setState(currentValues.fluidsEnabled);
		if (fluidCurrentStrengthSlider != null) fluidCurrentStrengthSlider.setValue(currentValues.fluidCurrentStrength);
		if (fluidBuoyancySlider != null) fluidBuoyancySlider.setValue(currentValues.fluidBuoyancy);
		if (fluidVisualsToggle != null) fluidVisualsToggle.setState(currentValues.fluidRenderStyle > 0);
		if (cubesPerBlockSlider != null) cubesPerBlockSlider.setValue(currentValues.fluidCubesPerBlock);
		if (fluidRenderRadiusSlider != null) fluidRenderRadiusSlider.setValue(currentValues.fluidRenderRadius);
		if (fluidMaxCubesSlider != null) fluidMaxCubesSlider.setValue(currentValues.fluidMaxCubes);
		if (fluidReplaceVanillaToggle != null) fluidReplaceVanillaToggle.setState(currentValues.fluidReplaceVanilla);
	}
	
	private void setStatusMessage(String message, int color) {
		this.statusMessage = message;
		this.statusColor = color;
		this.statusTime = System.currentTimeMillis();
	}
	
	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		
		// Panel background
		int surfaceColor = (0xCC << 24) | (Dex5Colors.SURFACE & 0x00FFFFFF);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, surfaceColor);
		
		// Watermark
		if (HAS_BRANDING) {
			renderWatermark(graphics);
		}
		
		// Border
		drawBorder(graphics, panelX, panelY, panelWidth, panelHeight, Dex5Colors.BORDER_ACCENT);
		
		// Title area
		int titleY = panelY + 12;
		graphics.drawCenteredString(this.font, this.title, this.width / 2, titleY, Dex5Colors.PRIMARY);
		String subtitle = canEdit ? "Configure Lightweight Physics Settings" : "View Configuration (Read-Only)";
		graphics.drawCenteredString(this.font, subtitle, this.width / 2, titleY + 12, Dex5Colors.TEXT_SECONDARY);
		
		// Enable scissor for scrollable content
		graphics.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
		
		// Render scrollable content
		renderScrollableContent(graphics, mouseX, mouseY);
		
		// Disable scissor
		graphics.disableScissor();
		
		// Draw scrollbar
		if (maxScrollOffset > 0) {
			drawScrollbar(graphics);
		}
		
		// Footer buttons (rendered by super.render)
		super.render(graphics, mouseX, mouseY, partialTick);
		
		// Status message
		if (!statusMessage.isEmpty()) {
			long elapsed = System.currentTimeMillis() - statusTime;
			if (elapsed < 3000) {
				int alpha = elapsed < 2500 ? 255 : (int) (255 * (1 - (elapsed - 2500) / 500.0));
				int color = (alpha << 24) | (statusColor & 0x00FFFFFF);
				int statusY = panelY + panelHeight - FOOTER_HEIGHT - 8;
				graphics.drawCenteredString(this.font, statusMessage, this.width / 2, statusY, color);
			}
		}
	}
	
	private void renderScrollableContent(GuiGraphics graphics, int mouseX, int mouseY) {
		int y = contentY - (int)scrollOffset;
		int widgetWidth = contentWidth - 40;
		int leftX = contentX + 20;
		int centerX = contentX + contentWidth / 2;
		
		// Main toggle
		positionAndRender(enabledToggle, centerX - 100, y, graphics, mouseX, mouseY);
		y += BIG_TOGGLE_HEIGHT + SECTION_SPACING;
		
		// Physics section
		drawSectionHeader(graphics, centerX, y, "Physics");
		y += SECTION_HEADER_HEIGHT + SECTION_SPACING;
		
		positionAndRender(radiusSlider, leftX, y, graphics, mouseX, mouseY);
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		positionAndRender(maxActiveSlider, leftX, y, graphics, mouseX, mouseY);
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		positionAndRender(updateIntervalSlider, leftX, y, graphics, mouseX, mouseY);
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		positionAndRender(sleepVelocitySlider, leftX, y, graphics, mouseX, mouseY);
		y += WIDGET_HEIGHT + SECTION_SPACING;
		
		// Debug section
		drawSectionHeader(graphics, centerX, y, "Debug");
		y += SECTION_HEADER_HEIGHT + SECTION_SPACING;
		
		positionAndRender(debugToggle, centerX - 80, y, graphics, mouseX, mouseY);
		y += WIDGET_HEIGHT + SECTION_SPACING;
		
		// Fluids (Server) section
		drawSectionHeader(graphics, centerX, y, "Fluids (Server)");
		y += SECTION_HEADER_HEIGHT + SECTION_SPACING;
		
		positionAndRender(fluidsToggle, centerX - 100, y, graphics, mouseX, mouseY);
		y += 30 + WIDGET_SPACING;
		
		positionAndRender(fluidCurrentStrengthSlider, leftX, y, graphics, mouseX, mouseY);
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		positionAndRender(fluidBuoyancySlider, leftX, y, graphics, mouseX, mouseY);
		y += WIDGET_HEIGHT + SECTION_SPACING;
		
		// Fluid Visuals (Client) section
		drawSectionHeader(graphics, centerX, y, "Fluid Visuals (Client)");
		y += SECTION_HEADER_HEIGHT + SECTION_SPACING;
		
		positionAndRender(fluidVisualsToggle, centerX - 90, y, graphics, mouseX, mouseY);
		y += 30 + WIDGET_SPACING;
		
		positionAndRender(cubesPerBlockSlider, leftX, y, graphics, mouseX, mouseY);
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		positionAndRender(fluidRenderRadiusSlider, leftX, y, graphics, mouseX, mouseY);
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		positionAndRender(fluidMaxCubesSlider, leftX, y, graphics, mouseX, mouseY);
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		positionAndRender(fluidReplaceVanillaToggle, centerX - 90, y, graphics, mouseX, mouseY);
	}
	
	private void positionAndRender(net.minecraft.client.gui.components.AbstractWidget widget, 
	                                int x, int y, GuiGraphics graphics, int mouseX, int mouseY) {
		if (widget == null) return;
		
		widget.setX(x);
		widget.setY(y);
		
		// Only render if visible in content area
		if (y + widget.getHeight() >= contentY && y <= contentY + contentHeight) {
			// Adjust mouse position for widget hover detection
			double adjustedMouseY = mouseY + scrollOffset;
			widget.render(graphics, mouseX, (int)adjustedMouseY, 0);
		}
	}
	
	private void drawSectionHeader(GuiGraphics graphics, int centerX, int y, String text) {
		// Only draw if visible
		if (y + SECTION_HEADER_HEIGHT >= contentY && y <= contentY + contentHeight) {
			graphics.drawCenteredString(this.font, text, centerX, y + 6, Dex5Colors.ACCENT);
		}
	}
	
	private void drawScrollbar(GuiGraphics graphics) {
		int scrollbarWidth = 4;
		int scrollbarX = contentX + contentWidth - scrollbarWidth - 2;
		int scrollbarHeight = contentHeight;
		
		// Track
		graphics.fill(scrollbarX, contentY, scrollbarX + scrollbarWidth, contentY + scrollbarHeight, 
		              0x40FFFFFF);
		
		// Thumb
		double thumbHeight = Math.max(20, scrollbarHeight * (contentHeight / (double)(contentHeight + maxScrollOffset)));
		double thumbY = contentY + (scrollbarHeight - thumbHeight) * (scrollOffset / maxScrollOffset);
		graphics.fill(scrollbarX, (int)thumbY, scrollbarX + scrollbarWidth, (int)(thumbY + thumbHeight), 
		              Dex5Colors.PRIMARY);
	}
	
	private void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
		graphics.fill(x - 1, y - 1, x + width + 1, y, color);
		graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, color);
		graphics.fill(x - 1, y, x, y + height, color);
		graphics.fill(x + width, y, x + width + 1, y + height, color);
	}
	
	@Override
	public void onClose() {
		if (isDirty && canEdit) {
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
		graphics.fill(0, 0, this.width, this.height, 0x33000000);
	}
	
	private void renderWatermark(GuiGraphics graphics) {
		try {
			int maxSize = (int)(Math.min(panelWidth, panelHeight) * 0.5);
			int watermarkSize = Math.min(maxSize, 256);
			
			int x = panelX + (panelWidth - watermarkSize) / 2;
			int y = panelY + (panelHeight - watermarkSize) / 2;
			
			graphics.setColor(1.0f, 1.0f, 1.0f, WATERMARK_ALPHA);
			
			graphics.blit(
				WATERMARK_TEXTURE,
				x, y,
				0, 0,
				watermarkSize, watermarkSize,
				watermarkSize, watermarkSize
			);
			
			graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
		} catch (Exception e) {
			// Silently ignore
		}
	}
	
	private static boolean checkBranding() {
		try {
			Minecraft.getInstance().getResourceManager()
				.getResource(WATERMARK_TEXTURE);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
