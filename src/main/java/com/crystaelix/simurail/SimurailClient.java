package com.crystaelix.simurail;

import com.crystaelix.simurail.client.FluidCubeRenderer;
import com.crystaelix.simurail.client.config.ClientConfigHelper;
import com.crystaelix.simurail.client.config.LightweightPhysicsConfigScreen;
import com.crystaelix.simurail.config.ClientConfigBridge;
import com.crystaelix.simurail.config.LightweightPhysicsConfigValues;
import com.crystaelix.simurail.content.SimurailInteractCallbacks;
import com.crystaelix.simurail.content.SimurailPartialModels;
import com.crystaelix.simurail.content.SimurailParticleProviders;
import com.crystaelix.simurail.ponder.SimurailPonderPlugin;

import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@Mod(value = Simurail.MOD_ID, dist = Dist.CLIENT)
public class SimurailClient {

	public SimurailClient(IEventBus modEventBus, ModContainer modContainer) {
		modEventBus.register(this);
		// Register custom Dex5-styled config screen for lightweight physics
		modContainer.registerExtensionPoint(IConfigScreenFactory.class, 
			(c, parent) -> new LightweightPhysicsConfigScreen(parent));
		PonderIndex.addPlugin(new SimurailPonderPlugin());
		
		// Register fluid cube renderer events
		NeoForge.EVENT_BUS.addListener(this::onClientTick);
		NeoForge.EVENT_BUS.addListener(this::onRenderLevel);
	}

	@SubscribeEvent
	public void onClientSetup(FMLCommonSetupEvent event) {
		SimurailPartialModels.register();
		SimurailInteractCallbacks.register();
		
		// Register client config bridge (Supplier pattern, no reflection)
		ClientConfigBridge.setClientAccessors(
			() -> {
				LightweightPhysicsConfigValues values = new LightweightPhysicsConfigValues();
				ClientConfigHelper.loadClientValues(values);
				return values;
			},
			ClientConfigHelper::applyClientValues
		);
	}

	@SubscribeEvent
	public void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
		SimurailParticleProviders.register(event);
	}

	public void onClientTick(LevelTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null && !mc.isPaused() && event.getLevel() == mc.level) {
			FluidCubeRenderer.tick(mc);
		}
	}

	public void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
			FluidCubeRenderer.render(event.getPoseStack(), event.getCamera(), 
				event.getPartialTick().getGameTimeDeltaPartialTick(false), event.getFrustum());
		}
	}
}
