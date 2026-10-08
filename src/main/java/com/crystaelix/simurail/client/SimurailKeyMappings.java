package com.crystaelix.simurail.client;

import org.lwjgl.glfw.GLFW;

import com.crystaelix.simurail.Simurail;
import com.crystaelix.simurail.client.config.LightweightPhysicsConfigScreen;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * Registers Simurail client keybindings.
 */
@EventBusSubscriber(modid = Simurail.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SimurailKeyMappings {
	
	public static final String CATEGORY = "key.categories.simurail";
	
	public static final KeyMapping OPEN_PHYSICS_CONFIG = new KeyMapping(
		"key.simurail.open_physics_config",
		KeyConflictContext.IN_GAME,
		InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_LEFT_BRACKET),
		CATEGORY
	);
	
	@SubscribeEvent
	public static void register(RegisterKeyMappingsEvent event) {
		event.register(OPEN_PHYSICS_CONFIG);
	}
}
