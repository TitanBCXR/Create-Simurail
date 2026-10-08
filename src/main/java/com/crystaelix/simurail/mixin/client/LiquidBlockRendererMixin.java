package com.crystaelix.simurail.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.crystaelix.simurail.config.SimurailConfig;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Mixin to suppress vanilla fluid tessellation when custom fluid rendering is enabled.
 * Target: LiquidBlockRenderer.tesselate
 */
@OnlyIn(Dist.CLIENT)
@Mixin(LiquidBlockRenderer.class)
public class LiquidBlockRendererMixin {
	
	@Inject(
		method = "tesselate",
		at = @At("HEAD"),
		cancellable = true
	)
	private void simurail$suppressVanillaFluidRendering(
			BlockAndTintGetter level, 
			BlockPos pos, 
			VertexConsumer buffer, 
			BlockState blockState, 
			FluidState fluidState,
			CallbackInfo ci) {
		try {
			boolean replaceVanilla = SimurailConfig.client().fluidVisualsReplaceVanilla.get();
			int renderStyle = SimurailConfig.client().fluidVisualsRenderStyle.get();
			
			if (replaceVanilla && renderStyle != 0) {
				ci.cancel();
			}
		} catch (Exception e) {
		}
	}
}
