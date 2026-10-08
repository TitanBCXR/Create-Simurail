package com.crystaelix.simurail.client.config;

import java.util.List;
import java.util.function.Consumer;

import com.crystaelix.simurail.client.FluidDebrisCatalog;
import com.crystaelix.simurail.client.FluidDebrisModels;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

/**
 * Scrollable debris-model list with a slowly rotating baked-model preview.
 */
public class DebrisModelPickerWidget extends AbstractWidget {

	private static final int ENTRY_HEIGHT = 16;
	private static final int PREVIEW_SIZE = 92;
	private static final int PREVIEW_GAP = 8;

	private final Consumer<String> onSelect;
	private String selectedId;
	private int listScroll;

	public DebrisModelPickerWidget(int x, int y, int width, int height, String selectedId, Consumer<String> onSelect) {
		super(x, y, width, height, Component.literal("Debris Model"));
		this.selectedId = selectedId == null ? FluidDebrisModels.WATER_CUBE : selectedId;
		this.onSelect = onSelect;
	}

	public void setSelectedId(String selectedId) {
		this.selectedId = selectedId == null ? FluidDebrisModels.WATER_CUBE : selectedId;
		ensureSelectedVisible();
	}

	public String getSelectedId() {
		return selectedId;
	}

	private List<FluidDebrisModels.PickerEntry> entries() {
		return FluidDebrisModels.entries();
	}

	private int listWidth() {
		return Math.max(80, getWidth() - PREVIEW_SIZE - PREVIEW_GAP);
	}

	private int maxListScroll() {
		int content = entries().size() * ENTRY_HEIGHT;
		return Math.max(0, content - getHeight());
	}

	private void ensureSelectedVisible() {
		List<FluidDebrisModels.PickerEntry> entries = entries();
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).id.equals(selectedId)) {
				int top = i * ENTRY_HEIGHT;
				if (top < listScroll) {
					listScroll = top;
				} else if (top + ENTRY_HEIGHT > listScroll + getHeight()) {
					listScroll = top + ENTRY_HEIGHT - getHeight();
				}
				listScroll = Mth.clamp(listScroll, 0, maxListScroll());
				return;
			}
		}
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		int listRight = getX() + listWidth();
		graphics.fill(getX(), getY(), listRight, getY() + getHeight(), 0xAA000000);
		drawBorder(graphics, getX(), getY(), listWidth(), getHeight(), Dex5Colors.BORDER_ACCENT);

		Font font = Minecraft.getInstance().font;
		List<FluidDebrisModels.PickerEntry> entries = entries();
		graphics.enableScissor(getX() + 1, getY() + 1, listRight - 1, getY() + getHeight() - 1);
		for (int i = 0; i < entries.size(); i++) {
			int y = getY() + i * ENTRY_HEIGHT - listScroll;
			if (y + ENTRY_HEIGHT < getY() || y > getY() + getHeight()) {
				continue;
			}
			FluidDebrisModels.PickerEntry entry = entries.get(i);
			boolean selected = entry.id.equals(selectedId);
			boolean hovered = mouseX >= getX() && mouseX < listRight && mouseY >= y && mouseY < y + ENTRY_HEIGHT
				&& mouseY >= getY() && mouseY < getY() + getHeight();
			if (selected) {
				graphics.fill(getX() + 1, y, listRight - 1, y + ENTRY_HEIGHT, 0x6000BFFF);
			} else if (hovered) {
				graphics.fill(getX() + 1, y, listRight - 1, y + ENTRY_HEIGHT, Dex5Colors.HOVER_OVERLAY);
			}
			int color = switch (entry.status) {
				case TOO_MANY, FAILED -> Dex5Colors.ERROR;
				case OK -> selected ? Dex5Colors.PRIMARY : Dex5Colors.TEXT;
			};
			String label = entry.displayName;
			String suffix = switch (entry.status) {
				case TOO_MANY -> "  " + entry.triangles + "  too many polys";
				case FAILED -> "  failed to load";
				case OK -> "  " + entry.triangles + " tris";
			};
			graphics.drawString(font, font.plainSubstrByWidth(label + suffix, listWidth() - 8), getX() + 4, y + 4, color, false);
		}
		graphics.disableScissor();

		int previewX = listRight + PREVIEW_GAP;
		int previewY = getY();
		graphics.fill(previewX, previewY, previewX + PREVIEW_SIZE, previewY + PREVIEW_SIZE, 0xCC111122);
		drawBorder(graphics, previewX, previewY, PREVIEW_SIZE, PREVIEW_SIZE, Dex5Colors.PRIMARY);
		renderPreview(graphics, previewX, previewY, PREVIEW_SIZE);
	}

	private void renderPreview(GuiGraphics graphics, int x, int y, int size) {
		Minecraft mc = Minecraft.getInstance();
		BakedModel model = FluidDebrisModels.previewModel(mc, selectedId);
		if (model == null) {
			graphics.drawCenteredString(mc.font, "?", x + size / 2, y + size / 2 - 4, Dex5Colors.ERROR);
			return;
		}
		int color = previewTint(mc, selectedId);
		float angle = (System.currentTimeMillis() % 8000L) / 8000.0f * 360.0f;

		graphics.enableScissor(x + 1, y + 1, x + size - 1, y + size - 1);
		PoseStack pose = graphics.pose();
		pose.pushPose();
		pose.translate(x + size / 2.0f, y + size / 2.0f, 100);
		pose.scale(size * 0.85f, -size * 0.85f, size * 0.85f);
		pose.mulPose(Axis.XP.rotationDegrees(25));
		pose.mulPose(Axis.YP.rotationDegrees(angle));
		pose.translate(-0.5f, -0.5f, -0.5f);

		Lighting.setupForEntityInInventory();
		RenderSystem.enableDepthTest();
		MultiBufferSource.BufferSource buffers = graphics.bufferSource();
		FluidDebrisModels.renderQuads(pose, buffers, model, color, 0x00F000F0, RenderType.translucent());
		graphics.flush();
		Lighting.setupFor3DItems();
		pose.popPose();
		graphics.disableScissor();
	}

	private static int previewTint(Minecraft mc, String selectedId) {
		int water = 0x3F76E4;
		try {
			if (mc.level != null && mc.player != null) {
				Biome biome = mc.level.getBiome(mc.player.blockPosition()).value();
				water = biome.getWaterColor();
			}
		} catch (Exception e) {
		}
		int wr = (water >> 16) & 0xFF;
		int wg = (water >> 8) & 0xFF;
		int wb = water & 0xFF;
		if (FluidDebrisModels.ICE_CUBE.equals(selectedId)) {
			int r = (255 * 3 + wr) / 4;
			int g = (255 * 3 + wg) / 4;
			int b = (255 * 3 + wb) / 4;
			return (200 << 24) | (r << 16) | (g << 8) | b;
		}
		return (200 << 24) | (wr << 16) | (wg << 8) | wb;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!isActive() || !visible || !clicked(mouseX, mouseY)) {
			return false;
		}
		int listRight = getX() + listWidth();
		if (mouseX >= listRight) {
			return true;
		}
		int index = ((int) mouseY - getY() + listScroll) / ENTRY_HEIGHT;
		List<FluidDebrisModels.PickerEntry> entries = entries();
		if (index >= 0 && index < entries.size()) {
			selectedId = entries.get(index).id;
			if (onSelect != null) {
				onSelect.accept(selectedId);
			}
			playDownSound(Minecraft.getInstance().getSoundManager());
			return true;
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!isMouseOver(mouseX, mouseY) || mouseX >= getX() + listWidth()) {
			return false;
		}
		listScroll = Mth.clamp(listScroll - (int) (scrollY * ENTRY_HEIGHT), 0, maxListScroll());
		return true;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.literal("Debris model " + selectedId));
	}

	private void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
		graphics.fill(x - 1, y - 1, x + width + 1, y, color);
		graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, color);
		graphics.fill(x - 1, y, x, y + height, color);
		graphics.fill(x + width, y, x + width + 1, y + height, color);
	}
}
