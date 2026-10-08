package com.crystaelix.simurail.client;

import java.util.List;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Registers the always-enabled built-in pack for custom fluid debris models.
 */
public final class FluidDebrisPack {

	public static final String PACK_ID = "simurail/fluid_debris_models";

	private FluidDebrisPack() {
	}

	public static void register(AddPackFindersEvent event) {
		if (event.getPackType() != PackType.CLIENT_RESOURCES) {
			return;
		}
		FluidDebrisCatalog.ensureFolderAndReadme();

		PackLocationInfo locationInfo = new PackLocationInfo(
			PACK_ID,
			Component.literal("Simurail Fluid Debris Models"),
			PackSource.BUILT_IN,
			Optional.empty()
		);
		Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
			@Override
			public net.minecraft.server.packs.PackResources openPrimary(PackLocationInfo location) {
				return new FluidDebrisPackResources(location);
			}

			@Override
			public net.minecraft.server.packs.PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
				return new FluidDebrisPackResources(location);
			}
		};
		Pack.Metadata metadata = new Pack.Metadata(
			Component.literal("Custom fluid debris models from config/simurail/fluid_models"),
			PackCompatibility.COMPATIBLE,
			FeatureFlagSet.of(),
			List.of()
		);
		Pack pack = new Pack(
			locationInfo,
			supplier,
			metadata,
			new PackSelectionConfig(true, Pack.Position.TOP, true)
		);
		event.addRepositorySource(consumer -> consumer.accept(pack));
	}
}
