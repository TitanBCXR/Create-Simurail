package com.crystaelix.simurail.client.config;

import com.crystaelix.simurail.Simurail;
import com.crystaelix.simurail.client.FluidDebrisCatalog;
import com.crystaelix.simurail.config.LightweightPhysicsConfigValues;
import com.crystaelix.simurail.network.SimurailPackets;
import com.crystaelix.simurail.network.UpdateLightweightPhysicsConfigPacket;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom config screen for Lightweight Physics settings.
 * Styled with Dex5 theme (high-contrast dark with vibrant accents).
 * Features scrollable content area with proper hit-testing.
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
	
	// Layout constants
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
	
	// Computed layout
	private int panelX, panelY, panelWidth, panelHeight;
	private int contentX, contentY, contentWidth, contentHeight;
	private double scrollOffset = 0;
	private double maxScrollOffset = 0;
	
	// Scrollable widgets with their base Y positions (before scroll)
	private final List<WidgetEntry> scrollableWidgets = new ArrayList<>();
	private final List<SectionHeader> sectionHeaders = new ArrayList<>();
	
	// Footer buttons (not scrollable)
	private Button resetButton;
	private Button saveButton;
	private Button cancelButton;
	
	// Widget references
	private Dex5Slider radiusSlider, maxActiveSlider, updateIntervalSlider, sleepVelocitySlider;
	private Dex5Slider fluidRenderRadiusSlider, fluidMaxCubesSlider, fluidDensitySlider;
	private Dex5Slider fluidDebrisScaleSlider, fluidDebrisSpinSpeedSlider;
	private Dex5Slider fluidWaveAmplitudeSlider, fluidWaveLengthSlider, fluidWaveSpeedSlider, fluidWakeStrengthSlider;
	private Dex5ToggleButton enabledToggle, debugToggle, fluidVisualsToggle, fluidEntityInteractionToggle;
	private DebrisModelPickerWidget debrisModelPicker;
	private Button openModelsFolderButton;
	private Button refreshModelsButton;
	private double restoreScroll = 0;
	
	private static class WidgetEntry {
		final AbstractWidget widget;
		final int baseY;
		
		WidgetEntry(AbstractWidget widget, int baseY) {
			this.widget = widget;
			this.baseY = baseY;
		}
	}
	
	private static class SectionHeader {
		final String text;
		final int baseY;
		
		SectionHeader(String text, int baseY) {
			this.text = text;
			this.baseY = baseY;
		}
	}
	
	public LightweightPhysicsConfigScreen(Screen parent) {
		this(parent, LightweightPhysicsConfigValues.loadFromConfig(), false, 0);
	}

	public LightweightPhysicsConfigScreen(Screen parent, LightweightPhysicsConfigValues pending, boolean dirty, double scroll) {
		super(Component.literal("Lightweight Physics Configuration"));
		this.parent = parent;
		this.originalValues = LightweightPhysicsConfigValues.loadFromConfig();
		this.currentValues = pending.copy();
		if (this.currentValues.fluidDebrisModel == null || this.currentValues.fluidDebrisModel.isBlank()) {
			this.currentValues.fluidDebrisModel = "builtin:water_cube";
		}
		this.isDirty = dirty;
		this.restoreScroll = scroll;
		
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
		
		// Compute layout
		panelWidth = Mth.clamp((int)(this.width * PANEL_WIDTH_RATIO), PANEL_MIN_WIDTH, PANEL_MAX_WIDTH);
		panelWidth = Math.min(panelWidth, this.width - 20);
		
		panelHeight = (int)(this.height * PANEL_HEIGHT_RATIO);
		panelHeight = Math.max(panelHeight, PANEL_MIN_HEIGHT);
		panelHeight = Math.min(panelHeight, this.height - 20);
		
		panelX = (this.width - panelWidth) / 2;
		panelY = (this.height - panelHeight) / 2;
		
		contentX = panelX + SCROLL_MARGIN;
		contentY = panelY + TITLE_HEIGHT;
		contentWidth = panelWidth - SCROLL_MARGIN * 2;
		contentHeight = panelHeight - TITLE_HEIGHT - FOOTER_HEIGHT;
		
		// Build scrollable content
		buildScrollableContent();
		
		// Calculate max scroll
		int totalHeight = scrollableWidgets.isEmpty() ? 0 : 
			scrollableWidgets.get(scrollableWidgets.size() - 1).baseY + 
			scrollableWidgets.get(scrollableWidgets.size() - 1).widget.getHeight();
		maxScrollOffset = Math.max(0, totalHeight - contentHeight);
		scrollOffset = Mth.clamp(restoreScroll, 0, maxScrollOffset);
		
		// Update widget positions for initial scroll
		updateScrollableWidgetPositions();
		
		// Add footer buttons (fixed, always on top)
		addFooterButtons();
		
		if (!canEdit) {
			setStatusMessage("Read-only: Requires operator permissions", Dex5Colors.WARNING);
		}
	}
	
	private void buildScrollableContent() {
		scrollableWidgets.clear();
		sectionHeaders.clear();
		
		int widgetWidth = contentWidth - 40;
		int leftX = contentX + 20;
		int centerX = contentX + contentWidth / 2;
		int y = 0;
		
		// Main enabled toggle
		enabledToggle = new Dex5ToggleButton(
			centerX - 100, contentY, 200, BIG_TOGGLE_HEIGHT,
			Component.literal("Lightweight Physics"),
			currentValues.enabled,
			button -> {
				currentValues.enabled = !currentValues.enabled;
				((Dex5ToggleButton) button).setState(currentValues.enabled);
				markDirty();
			}
		);
		enabledToggle.active = canEdit;
		scrollableWidgets.add(new WidgetEntry(enabledToggle, y));
		y += BIG_TOGGLE_HEIGHT + SECTION_SPACING;
		
		// Physics section
		sectionHeaders.add(new SectionHeader("Physics", y));
		y += SECTION_HEADER_HEIGHT + SECTION_SPACING;
		
		radiusSlider = new Dex5Slider(
			leftX, contentY, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Activation Radius: "),
			Component.literal(" m"),
			0, 256, currentValues.activationRadius,
			value -> {
				currentValues.activationRadius = value.floatValue();
				markDirty();
			}
		);
		radiusSlider.active = canEdit;
		scrollableWidgets.add(new WidgetEntry(radiusSlider, y));
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		maxActiveSlider = new Dex5Slider(
			leftX, contentY, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Max Active Objects: "),
			Component.literal(""),
			0, 2048, currentValues.maxActive,
			value -> {
				currentValues.maxActive = value.intValue();
				markDirty();
			}
		);
		maxActiveSlider.active = canEdit;
		scrollableWidgets.add(new WidgetEntry(maxActiveSlider, y));
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		updateIntervalSlider = new Dex5Slider(
			leftX, contentY, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Update Interval: "),
			Component.literal(" ticks"),
			1, 20, currentValues.updateInterval,
			value -> {
				currentValues.updateInterval = value.intValue();
				markDirty();
			}
		);
		updateIntervalSlider.active = canEdit;
		scrollableWidgets.add(new WidgetEntry(updateIntervalSlider, y));
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		sleepVelocitySlider = new Dex5Slider(
			leftX, contentY, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Sleep Velocity: "),
			Component.literal(" m/s"),
			0, 10, currentValues.sleepVelocity,
			value -> {
				currentValues.sleepVelocity = value.floatValue();
				markDirty();
			},
			true
		);
		sleepVelocitySlider.active = canEdit;
		scrollableWidgets.add(new WidgetEntry(sleepVelocitySlider, y));
		y += WIDGET_HEIGHT + SECTION_SPACING;
		
		// Debug section
		sectionHeaders.add(new SectionHeader("Debug", y));
		y += SECTION_HEADER_HEIGHT + SECTION_SPACING;
		
		debugToggle = new Dex5ToggleButton(
			centerX - 80, contentY, 160, WIDGET_HEIGHT,
			Component.literal("Debug Logging"),
			currentValues.debugLogging,
			button -> {
				currentValues.debugLogging = !currentValues.debugLogging;
				((Dex5ToggleButton) button).setState(currentValues.debugLogging);
				markDirty();
			}
		);
		debugToggle.active = canEdit;
		scrollableWidgets.add(new WidgetEntry(debugToggle, y));
		y += WIDGET_HEIGHT + SECTION_SPACING;
		
		// Fluids (Client) section - all fluid settings are client-side rendering options
		sectionHeaders.add(new SectionHeader("Fluids (Client)", y));
		y += SECTION_HEADER_HEIGHT + SECTION_SPACING;
		
		fluidVisualsToggle = new Dex5ToggleButton(
			centerX - 90, contentY, 180, 30,
			Component.literal("Floating Cubes"),
			currentValues.fluidRenderStyle > 0,
			button -> {
				currentValues.fluidRenderStyle = (currentValues.fluidRenderStyle == 0) ? 1 : 0;
				((Dex5ToggleButton) button).setState(currentValues.fluidRenderStyle > 0);
				markDirty();
			}
		);
		fluidVisualsToggle.active = true;
		scrollableWidgets.add(new WidgetEntry(fluidVisualsToggle, y));
		y += 30 + WIDGET_SPACING;

		sectionHeaders.add(new SectionHeader("Debris Model", y));
		y += SECTION_HEADER_HEIGHT + WIDGET_SPACING;

		debrisModelPicker = new DebrisModelPickerWidget(
			leftX, contentY, widgetWidth, 110,
			currentValues.fluidDebrisModel,
			id -> {
				currentValues.fluidDebrisModel = id;
				markDirty();
			}
		);
		scrollableWidgets.add(new WidgetEntry(debrisModelPicker, y));
		y += 110 + WIDGET_SPACING;

		int folderButtonWidth = (widgetWidth - 8) / 2;
		openModelsFolderButton = Button.builder(
			Component.literal("Open folder"),
			button -> {
				try {
					FluidDebrisCatalog.ensureFolderAndReadme();
					Util.getPlatform().openFile(FluidDebrisCatalog.modelsFolder().toFile());
				} catch (Exception e) {
					setStatusMessage("Could not open folder", Dex5Colors.ERROR);
				}
			}
		).bounds(leftX, contentY, folderButtonWidth, WIDGET_HEIGHT).build();
		scrollableWidgets.add(new WidgetEntry(openModelsFolderButton, y));

		refreshModelsButton = Button.builder(
			Component.literal("Refresh"),
			button -> refreshCustomModels()
		).bounds(leftX + folderButtonWidth + 8, contentY, folderButtonWidth, WIDGET_HEIGHT).build();
		scrollableWidgets.add(new WidgetEntry(refreshModelsButton, y));
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		fluidDensitySlider = new Dex5Slider(
			leftX, contentY, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Density: "),
			Component.literal(" cubes/block"),
			0.1, 4.0, currentValues.fluidDensity,
			value -> {
				currentValues.fluidDensity = value.floatValue();
				markDirty();
			},
			true
		);
		fluidDensitySlider.active = true;
		scrollableWidgets.add(new WidgetEntry(fluidDensitySlider, y));
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		fluidDebrisScaleSlider = new Dex5Slider(
			leftX, contentY, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Debris Scale: "),
			Component.literal(" blocks"),
			0.1, 1.0, currentValues.fluidDebrisScale,
			value -> {
				currentValues.fluidDebrisScale = value.floatValue();
				markDirty();
			},
			true
		);
		fluidDebrisScaleSlider.active = true;
		scrollableWidgets.add(new WidgetEntry(fluidDebrisScaleSlider, y));
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		fluidDebrisSpinSpeedSlider = new Dex5Slider(
			leftX, contentY, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Spin Speed: "),
			Component.literal("x"),
			0.0, 2.0, currentValues.fluidDebrisSpinSpeed,
			value -> {
				currentValues.fluidDebrisSpinSpeed = value.floatValue();
				markDirty();
			},
			true
		);
		fluidDebrisSpinSpeedSlider.active = true;
		scrollableWidgets.add(new WidgetEntry(fluidDebrisSpinSpeedSlider, y));
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		fluidWaveAmplitudeSlider = new Dex5Slider(
			leftX, contentY, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Wave Amplitude: "),
			Component.literal(" blocks"),
			0.0, 1.0, currentValues.fluidWaveAmplitude,
			value -> {
				currentValues.fluidWaveAmplitude = value.floatValue();
				markDirty();
			},
			true
		);
		fluidWaveAmplitudeSlider.active = true;
		scrollableWidgets.add(new WidgetEntry(fluidWaveAmplitudeSlider, y));
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		fluidWaveLengthSlider = new Dex5Slider(
			leftX, contentY, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Wave Length: "),
			Component.literal(" blocks"),
			1.0, 8.0, currentValues.fluidWaveLength,
			value -> {
				currentValues.fluidWaveLength = value.floatValue();
				markDirty();
			},
			true
		);
		fluidWaveLengthSlider.active = true;
		scrollableWidgets.add(new WidgetEntry(fluidWaveLengthSlider, y));
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		fluidWaveSpeedSlider = new Dex5Slider(
			leftX, contentY, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Wave Speed: "),
			Component.literal("x"),
			0.0, 2.0, currentValues.fluidWaveSpeed,
			value -> {
				currentValues.fluidWaveSpeed = value.floatValue();
				markDirty();
			},
			true
		);
		fluidWaveSpeedSlider.active = true;
		scrollableWidgets.add(new WidgetEntry(fluidWaveSpeedSlider, y));
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		fluidEntityInteractionToggle = new Dex5ToggleButton(
			centerX - 90, contentY, 180, 30,
			Component.literal("Entity Interaction"),
			currentValues.fluidEntityInteraction,
			button -> {
				currentValues.fluidEntityInteraction = !currentValues.fluidEntityInteraction;
				((Dex5ToggleButton) button).setState(currentValues.fluidEntityInteraction);
				markDirty();
			}
		);
		fluidEntityInteractionToggle.active = true;
		scrollableWidgets.add(new WidgetEntry(fluidEntityInteractionToggle, y));
		y += 30 + WIDGET_SPACING;
		
		fluidWakeStrengthSlider = new Dex5Slider(
			leftX, contentY, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Wake Strength: "),
			Component.literal("x"),
			0.0, 2.0, currentValues.fluidWakeStrength,
			value -> {
				currentValues.fluidWakeStrength = value.floatValue();
				markDirty();
			},
			true
		);
		fluidWakeStrengthSlider.active = true;
		scrollableWidgets.add(new WidgetEntry(fluidWakeStrengthSlider, y));
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		fluidRenderRadiusSlider = new Dex5Slider(
			leftX, contentY, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Render Radius: "),
			Component.literal(" blocks"),
			4, 64, currentValues.fluidRenderRadius,
			value -> {
				currentValues.fluidRenderRadius = value.intValue();
				markDirty();
			}
		);
		fluidRenderRadiusSlider.active = true;
		scrollableWidgets.add(new WidgetEntry(fluidRenderRadiusSlider, y));
		y += WIDGET_HEIGHT + WIDGET_SPACING;
		
		fluidMaxCubesSlider = new Dex5Slider(
			leftX, contentY, widgetWidth, WIDGET_HEIGHT,
			Component.literal("Max Cubes: "),
			Component.literal(""),
			64, 4096, currentValues.fluidMaxCubes,
			value -> {
				currentValues.fluidMaxCubes = value.intValue();
				markDirty();
			}
		);
		fluidMaxCubesSlider.active = true;
		scrollableWidgets.add(new WidgetEntry(fluidMaxCubesSlider, y));
	}
	
	/**
	 * Update widget Y positions and visibility based on current scroll offset.
	 * This ensures hit-testing matches visual rendering.
	 */
	private void updateScrollableWidgetPositions() {
		for (WidgetEntry entry : scrollableWidgets) {
			int screenY = contentY + entry.baseY - (int)scrollOffset;
			entry.widget.setY(screenY);
			
			// Widget is visible if it intersects the content viewport
			boolean visible = screenY + entry.widget.getHeight() >= contentY && 
			                  screenY <= contentY + contentHeight;
			entry.widget.visible = visible;
		}
	}
	
	private void addFooterButtons() {
		int buttonY = panelY + panelHeight - FOOTER_HEIGHT + (FOOTER_HEIGHT - BUTTON_HEIGHT) / 2;
		int centerX = this.width / 2;
		int spacing = 10;
		int totalWidth = BUTTON_WIDTH * 3 + spacing * 2;
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
		.bounds(startX + BUTTON_WIDTH + spacing, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
		.build();
		saveButton.active = canEdit && isDirty;
		addRenderableWidget(saveButton);
		
		cancelButton = Button.builder(
			Component.literal("Cancel"),
			button -> onClose()
		)
		.bounds(startX + (BUTTON_WIDTH + spacing) * 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
		.build();
		addRenderableWidget(cancelButton);
	}
	
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (debrisModelPicker != null && debrisModelPicker.visible
				&& debrisModelPicker.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
			return true;
		}
		// Only scroll if mouse is in content area
		if (mouseX >= contentX && mouseX <= contentX + contentWidth &&
		    mouseY >= contentY && mouseY <= contentY + contentHeight) {
			scrollOffset = Mth.clamp(scrollOffset - scrollY * 20, 0, maxScrollOffset);
			updateScrollableWidgetPositions();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}
	
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// Check footer buttons first (they have priority)
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		
		// Only handle scrollable widgets if click is in content area
		if (mouseX >= contentX && mouseX <= contentX + contentWidth &&
		    mouseY >= contentY && mouseY <= contentY + contentHeight) {
			// Check scrollable widgets (their positions are already updated)
			for (WidgetEntry entry : scrollableWidgets) {
				if (entry.widget.visible && entry.widget.active && entry.widget.mouseClicked(mouseX, mouseY, button)) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		// Let footer buttons handle drag first
		if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
			return true;
		}
		
		// Check scrollable widgets
		for (WidgetEntry entry : scrollableWidgets) {
			if (entry.widget.visible && entry.widget.active && entry.widget.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
				return true;
			}
		}
		
		return false;
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
		
		currentValues.applyToConfig();
		
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
		if (fluidVisualsToggle != null) fluidVisualsToggle.setState(currentValues.fluidRenderStyle > 0);
		if (debrisModelPicker != null) debrisModelPicker.setSelectedId(currentValues.fluidDebrisModel);
		if (fluidDensitySlider != null) fluidDensitySlider.setValue(currentValues.fluidDensity);
		if (fluidDebrisScaleSlider != null) fluidDebrisScaleSlider.setValue(currentValues.fluidDebrisScale);
		if (fluidDebrisSpinSpeedSlider != null) fluidDebrisSpinSpeedSlider.setValue(currentValues.fluidDebrisSpinSpeed);
		if (fluidWaveAmplitudeSlider != null) fluidWaveAmplitudeSlider.setValue(currentValues.fluidWaveAmplitude);
		if (fluidWaveLengthSlider != null) fluidWaveLengthSlider.setValue(currentValues.fluidWaveLength);
		if (fluidWaveSpeedSlider != null) fluidWaveSpeedSlider.setValue(currentValues.fluidWaveSpeed);
		if (fluidEntityInteractionToggle != null) fluidEntityInteractionToggle.setState(currentValues.fluidEntityInteraction);
		if (fluidWakeStrengthSlider != null) fluidWakeStrengthSlider.setValue(currentValues.fluidWakeStrength);
		if (fluidRenderRadiusSlider != null) fluidRenderRadiusSlider.setValue(currentValues.fluidRenderRadius);
		if (fluidMaxCubesSlider != null) fluidMaxCubesSlider.setValue(currentValues.fluidMaxCubes);
	}
	
	private void refreshCustomModels() {
		FluidDebrisCatalog.ensureFolderAndReadme();
		FluidDebrisCatalog.scan();
		setStatusMessage("Reloading resource packs...", Dex5Colors.PRIMARY);
		Minecraft mc = Minecraft.getInstance();
		Screen parentScreen = this.parent;
		LightweightPhysicsConfigValues snapshot = currentValues.copy();
		boolean dirty = isDirty;
		double scroll = scrollOffset;
		mc.reloadResourcePacks().whenComplete((unused, error) -> mc.execute(() -> {
			if (error != null) {
				mc.setScreen(new LightweightPhysicsConfigScreen(parentScreen, snapshot, dirty, scroll));
				if (mc.screen instanceof LightweightPhysicsConfigScreen screen) {
					screen.setStatusMessage("Reload failed: " + error.getMessage(), Dex5Colors.ERROR);
				}
				return;
			}
			mc.setScreen(new LightweightPhysicsConfigScreen(parentScreen, snapshot, dirty, scroll));
			if (mc.screen instanceof LightweightPhysicsConfigScreen screen) {
				screen.setStatusMessage("Models refreshed", Dex5Colors.SUCCESS);
			}
		}));
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
		
		// Title
		int titleY = panelY + 12;
		graphics.drawCenteredString(this.font, this.title, this.width / 2, titleY, Dex5Colors.PRIMARY);
		String subtitle = canEdit ? "Configure Lightweight Physics Settings" : "View Configuration (Read-Only)";
		graphics.drawCenteredString(this.font, subtitle, this.width / 2, titleY + 12, Dex5Colors.TEXT_SECONDARY);
		
		// Enable scissor for content area
		graphics.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
		
		// Render section headers
		int centerX = contentX + contentWidth / 2;
		for (SectionHeader header : sectionHeaders) {
			int screenY = contentY + header.baseY - (int)scrollOffset;
			if (screenY + SECTION_HEADER_HEIGHT >= contentY && screenY <= contentY + contentHeight) {
				graphics.drawCenteredString(this.font, header.text, centerX, screenY + 6, Dex5Colors.ACCENT);
			}
		}
		
		// Render scrollable widgets (positions already updated)
		for (WidgetEntry entry : scrollableWidgets) {
			if (entry.widget.visible) {
				entry.widget.render(graphics, mouseX, mouseY, partialTick);
			}
		}
		
		graphics.disableScissor();
		
		// Scrollbar
		if (maxScrollOffset > 0) {
			drawScrollbar(graphics);
		}
		
		// Footer buttons
		super.render(graphics, mouseX, mouseY, partialTick);
		
		// Status message
		if (!statusMessage.isEmpty()) {
			long elapsed = System.currentTimeMillis() - statusTime;
			if (elapsed < 3000) {
				int alpha = elapsed < 2500 ? 255 : (int)(255 * (1 - (elapsed - 2500) / 500.0));
				int color = (alpha << 24) | (statusColor & 0x00FFFFFF);
				int statusY = panelY + panelHeight - FOOTER_HEIGHT - 8;
				graphics.drawCenteredString(this.font, statusMessage, this.width / 2, statusY, color);
			}
		}
	}
	
	private void drawScrollbar(GuiGraphics graphics) {
		int scrollbarWidth = 4;
		int scrollbarX = contentX + contentWidth - scrollbarWidth - 2;
		
		// Track
		graphics.fill(scrollbarX, contentY, scrollbarX + scrollbarWidth, contentY + contentHeight, 0x40FFFFFF);
		
		// Thumb
		int totalContentHeight = scrollableWidgets.isEmpty() ? contentHeight : 
			scrollableWidgets.get(scrollableWidgets.size() - 1).baseY + 
			scrollableWidgets.get(scrollableWidgets.size() - 1).widget.getHeight();
		double thumbHeight = Math.max(20, contentHeight * (contentHeight / (double)totalContentHeight));
		double thumbY = contentY + (contentHeight - thumbHeight) * (scrollOffset / maxScrollOffset);
		graphics.fill(scrollbarX, (int)thumbY, scrollbarX + scrollbarWidth, (int)(thumbY + thumbHeight), Dex5Colors.PRIMARY);
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
			graphics.blit(WATERMARK_TEXTURE, x, y, 0, 0, watermarkSize, watermarkSize, watermarkSize, watermarkSize);
			graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
		} catch (Exception e) {
			// Silently ignore
		}
	}
	
	private static boolean checkBranding() {
		try {
			Minecraft.getInstance().getResourceManager().getResource(WATERMARK_TEXTURE);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
