package com.crystaelix.simurail.network;

import com.crystaelix.simurail.Simurail;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers Simurail network packets.
 */
@EventBusSubscriber(modid = Simurail.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class SimurailPackets {
	
	@SubscribeEvent
	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar(Simurail.MOD_ID)
			.versioned("1.0")
			.optional();
		
		// C2S: Update lightweight physics config (server handler)
		registrar.playToServer(
			UpdateLightweightPhysicsConfigPacket.TYPE,
			UpdateLightweightPhysicsConfigPacket.STREAM_CODEC,
			UpdateLightweightPhysicsConfigPacket::handle
		);
	}
	
	public static void sendToServer(CustomPacketPayload payload) {
		net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
	}
}
