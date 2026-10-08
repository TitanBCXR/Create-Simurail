package com.crystaelix.simurail.network;

import com.crystaelix.simurail.Simurail;
import com.crystaelix.simurail.config.LightweightPhysicsConfigValues;
import com.crystaelix.simurail.config.SimurailConfig;

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
 */
public record UpdateLightweightPhysicsConfigPacket(
	boolean enabled,
	float activationRadius,
	int maxActive,
	int updateInterval,
	float sleepVelocity,
	boolean debugLogging
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
					ByteBufCodecs.BOOL.decode(buf)
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
			}
		};
	
	public UpdateLightweightPhysicsConfigPacket(LightweightPhysicsConfigValues values) {
		this(
			values.enabled,
			values.activationRadius,
			values.maxActive,
			values.updateInterval,
			values.sleepVelocity,
			values.debugLogging
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
				// (Always allowed in singleplayer for the integrated server owner)
				if (!serverPlayer.hasPermissions(2)) {
					serverPlayer.sendSystemMessage(
						Component.literal("§cError: You need operator permissions to change server config")
					);
					return;
				}
				
				// Validate and clamp values
				boolean enabled = packet.enabled;
				float activationRadius = Math.max(0, Math.min(256, packet.activationRadius));
				int maxActive = Math.max(0, Math.min(2048, packet.maxActive));
				int updateInterval = Math.max(1, Math.min(20, packet.updateInterval));
				float sleepVelocity = Math.max(0, Math.min(10, packet.sleepVelocity));
				boolean debugLogging = packet.debugLogging;
				
				// Apply server-side physics settings only
				SimurailConfig.server().physics.lightweightEnabled.set(enabled);
				SimurailConfig.server().physics.lightweightActivationRadius.set((double) activationRadius);
				SimurailConfig.server().physics.lightweightMaxActive.set(maxActive);
				SimurailConfig.server().physics.lightweightUpdateInterval.set(updateInterval);
				SimurailConfig.server().physics.lightweightSleepVelocity.set((double) sleepVelocity);
				SimurailConfig.server().physics.lightweightDebugLogging.set(debugLogging);
				
				// Save the server config spec to disk
				// In singleplayer: saves/<world>/serverconfig/simurail-server.toml
				// On dedicated server: config/simurail-server.toml
				com.crystaelix.simurail.config.SimurailConfig.server().specification.save();
				
				serverPlayer.sendSystemMessage(
					Component.literal("§aLightweight physics config updated and saved successfully")
				);
			}
		});
	}
}
