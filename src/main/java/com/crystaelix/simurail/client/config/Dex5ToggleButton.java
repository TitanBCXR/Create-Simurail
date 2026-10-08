package com.crystaelix.simurail.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Dex5-styled toggle button with distinct ON/OFF states.
 */
public class Dex5ToggleButton extends Button {
	
	private boolean state;
	
	public Dex5ToggleButton(int x, int y, int width, int height, Component message, boolean initialState, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
		this.state = initialState;
	}
	
	public void setState(boolean state) {
		this.state = state;
	}
	
	public boolean getState() {
		return state;
	}
	
	@Override
	public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Background color based on state
		int bgColor = !this.active ? Dex5Colors.TOGGLE_DISABLED :
		              state ? Dex5Colors.TOGGLE_ON : Dex5Colors.TOGGLE_OFF;
		
		// Hover effect
		if (this.isHovered && this.active) {
			bgColor = blendColor(bgColor, Dex5Colors.HOVER_OVERLAY);
		}
		
		// Draw background
		graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
		
		// Draw border
		int borderColor = state ? Dex5Colors.SUCCESS : Dex5Colors.BORDER_ACCENT;
		drawBorder(graphics, getX(), getY(), getWidth(), getHeight(), borderColor);
		
		// Draw text
		int textColor = this.active ? Dex5Colors.TEXT : Dex5Colors.TEXT_SECONDARY;
		String label = getMessage().getString() + ": " + (state ? "ON" : "OFF");
		graphics.drawCenteredString(
			Minecraft.getInstance().font,
			label,
			getX() + getWidth() / 2,
			getY() + (getHeight() - 8) / 2,
			textColor
		);
	}
	
	private void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
		graphics.fill(x - 1, y - 1, x + width + 1, y, color);
		graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, color);
		graphics.fill(x - 1, y, x, y + height, color);
		graphics.fill(x + width, y, x + width + 1, y + height, color);
	}
	
	private int blendColor(int base, int overlay) {
		int baseA = (base >> 24) & 0xFF;
		int baseR = (base >> 16) & 0xFF;
		int baseG = (base >> 8) & 0xFF;
		int baseB = base & 0xFF;
		
		int overlayA = (overlay >> 24) & 0xFF;
		int overlayR = (overlay >> 16) & 0xFF;
		int overlayG = (overlay >> 8) & 0xFF;
		int overlayB = overlay & 0xFF;
		
		float alpha = overlayA / 255.0f;
		int r = (int) (overlayR * alpha + baseR * (1 - alpha));
		int g = (int) (overlayG * alpha + baseG * (1 - alpha));
		int b = (int) (overlayB * alpha + baseB * (1 - alpha));
		
		return (baseA << 24) | (r << 16) | (g << 8) | b;
	}
}
