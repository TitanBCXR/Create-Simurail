package com.crystaelix.simurail.client;

import com.crystaelix.simurail.Simurail;
import com.crystaelix.simurail.client.config.LightweightPhysicsConfigScreen;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Handles client-side events for Simurail.
 */
@EventBusSubscriber(modid = Simurail.MOD_ID, value = Dist.CLIENT)
public class SimurailClientEvents {
	
	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		
		// Check for physics config keybind
		while (SimurailKeyMappings.OPEN_PHYSICS_CONFIG.consumeClick()) {
			if (mc.screen == null) {
				mc.setScreen(new LightweightPhysicsConfigScreen(null));
			}
		}
	}
}
