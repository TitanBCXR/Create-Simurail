package com.crystaelix.simurail.client.config;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Dex5-styled slider with custom rendering and value display.
 */
public class Dex5Slider extends AbstractSliderButton {
	
	private final Component prefix;
	private final Component suffix;
	private final double minValue;
	private final double maxValue;
	private final Consumer<Double> onChange;
	private final boolean isInteger;
	
	public Dex5Slider(int x, int y, int width, int height, 
	                   Component prefix, Component suffix,
	                   double minValue, double maxValue, double initialValue,
	                   Consumer<Double> onChange) {
		super(x, y, width, height, Component.empty(), 
		      (initialValue - minValue) / (maxValue - minValue));
		this.prefix = prefix;
		this.suffix = suffix;
		this.minValue = minValue;
		this.maxValue = maxValue;
		this.onChange = onChange;
		this.isInteger = (minValue == Math.floor(minValue) && maxValue == Math.floor(maxValue));
		updateMessage();
	}
	
	public void setValue(double value) {
		this.value = Mth.clamp((value - minValue) / (maxValue - minValue), 0, 1);
		updateMessage();
	}
	
	public double getValue() {
		return minValue + value * (maxValue - minValue);
	}
	
	@Override
	protected void updateMessage() {
		double currentValue = getValue();
		String valueStr = isInteger ? 
			String.format("%d", (int) Math.round(currentValue)) :
			String.format("%.2f", currentValue);
		
		setMessage(Component.literal(
			prefix.getString() + valueStr + suffix.getString()
		));
	}
	
	@Override
	protected void applyValue() {
		if (onChange != null) {
			double currentValue = getValue();
			if (isInteger) {
				onChange.accept((double) Math.round(currentValue));
			} else {
				onChange.accept(currentValue);
			}
		}
	}
	
	@Override
	public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Background
		int bgColor = this.active ? Dex5Colors.SURFACE : Dex5Colors.TOGGLE_DISABLED;
		graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
		
		// Progress bar
		int barWidth = (int) (getWidth() * value);
		int barColor = this.active ? Dex5Colors.PRIMARY : Dex5Colors.TEXT_SECONDARY;
		graphics.fill(getX(), getY(), getX() + barWidth, getY() + getHeight(), barColor | 0x40000000);
		
		// Border
		int borderColor = this.isHovered && this.active ? Dex5Colors.ACCENT : Dex5Colors.BORDER_ACCENT;
		drawBorder(graphics, getX(), getY(), getWidth(), getHeight(), borderColor);
		
		// Handle
		int handleX = getX() + (int) (value * (getWidth() - 4));
		int handleColor = this.active ? Dex5Colors.ACCENT : Dex5Colors.TEXT_SECONDARY;
		graphics.fill(handleX, getY() + 2, handleX + 4, getY() + getHeight() - 2, handleColor);
		
		// Text
		int textColor = this.active ? Dex5Colors.TEXT : Dex5Colors.TEXT_SECONDARY;
		graphics.drawString(
			Minecraft.getInstance().font,
			getMessage(),
			getX() + 6,
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
}
