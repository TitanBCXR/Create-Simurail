package com.crystaelix.simurail.network;

import com.crystaelix.simurail.Simurail;
import com.crystaelix.simurail.client.config.LightweightPhysicsConfigValues;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S packet to update lightweight physics config values.
 * Only operators (permission level 2+) can change server config.
 * Client config values are applied locally without server validation.
 */
public record UpdateLightweightPhysicsConfigPacket(
	boolean enabled,
	float activationRadius,
	int maxActive,
	int updateInterval,
	float sleepVelocity,
	boolean debugLogging,
	boolean fluidsEnabled,
	float fluidCurrentStrength,
	float fluidBuoyancy
) implements CustomPacketPayload {
	
	public static final Type<UpdateLightweightPhysicsConfigPacket> TYPE = 
		new Type<>(Simurail.id("update_lightweight_physics_config"));
	
	public static final StreamCodec<ByteBuf, UpdateLightweightPhysicsConfigPacket> STREAM_CODEC = 
		new StreamCodec<ByteBuf, UpdateLightweightPhysicsConfigPacket>() {
			@Override
			public UpdateLightweightPhysicsConfigPacket decode(ByteBuf buf) {
				return new UpdateLightweightPhysicsConfigPacket(
					ByteBufCodecs.BOOL.decode(buf),
					ByteBufCodecs.FLOAT.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.FLOAT.decode(buf),
					ByteBufCodecs.BOOL.decode(buf),
					ByteBufCodecs.BOOL.decode(buf),
					ByteBufCodecs.FLOAT.decode(buf),
					ByteBufCodecs.FLOAT.decode(buf)
				);
			}
			
			@Override
			public void encode(ByteBuf buf, UpdateLightweightPhysicsConfigPacket packet) {
				ByteBufCodecs.BOOL.encode(buf, packet.enabled);
				ByteBufCodecs.FLOAT.encode(buf, packet.activationRadius);
				ByteBufCodecs.VAR_INT.encode(buf, packet.maxActive);
				ByteBufCodecs.VAR_INT.encode(buf, packet.updateInterval);
				ByteBufCodecs.FLOAT.encode(buf, packet.sleepVelocity);
				ByteBufCodecs.BOOL.encode(buf, packet.debugLogging);
				ByteBufCodecs.BOOL.encode(buf, packet.fluidsEnabled);
				ByteBufCodecs.FLOAT.encode(buf, packet.fluidCurrentStrength);
				ByteBufCodecs.FLOAT.encode(buf, packet.fluidBuoyancy);
			}
		};
	
	public UpdateLightweightPhysicsConfigPacket(LightweightPhysicsConfigValues values) {
		this(
			values.enabled,
			values.activationRadius,
			values.maxActive,
			values.updateInterval,
			values.sleepVelocity,
			values.debugLogging,
			values.fluidsEnabled,
			values.fluidCurrentStrength,
			values.fluidBuoyancy
		);
	}
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
	
	public static void handle(UpdateLightweightPhysicsConfigPacket packet, IPayloadContext context) {
		context.enqueueWork(() -> {
			// Must be on server side
			if (context.flow().isServerbound() && context.player() instanceof ServerPlayer serverPlayer) {
				// Permission check: require operator level 2+
				if (!serverPlayer.hasPermissions(2)) {
					serverPlayer.sendSystemMessage(
						Component.literal("§cError: You need operator permissions to change server config")
					);
					return;
				}
				
				// Validate values
				LightweightPhysicsConfigValues values = new LightweightPhysicsConfigValues();
				values.enabled = packet.enabled;
				values.activationRadius = packet.activationRadius;
				values.maxActive = packet.maxActive;
				values.updateInterval = packet.updateInterval;
				values.sleepVelocity = packet.sleepVelocity;
				values.debugLogging = packet.debugLogging;
				values.fluidsEnabled = packet.fluidsEnabled;
				values.fluidCurrentStrength = packet.fluidCurrentStrength;
				values.fluidBuoyancy = packet.fluidBuoyancy;
				
				if (!values.validate()) {
					serverPlayer.sendSystemMessage(
						Component.literal("§cError: Invalid config values (out of range)")
					);
					return;
				}
				
				// Apply to server config
				values.applyToConfig();
				
				serverPlayer.sendSystemMessage(
					Component.literal("§aLightweight physics config updated successfully")
				);
			}
		});
	}
}
