package com.crystaelix.simurail.client;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * Always-on virtual pack mapping {@code config/simurail/fluid_models/} into simurail assets.
 */
public final class FluidDebrisPackResources extends AbstractPackResources {

	private final Map<ResourceLocation, IoSupplier<InputStream>> resources;

	public FluidDebrisPackResources(PackLocationInfo location) {
		super(location);
		FluidDebrisCatalog.scan();
		this.resources = FluidDebrisCatalog.resources();
	}

	@Override
	public IoSupplier<InputStream> getRootResource(String... path) {
		if (path.length == 1 && "pack.mcmeta".equals(path[0])) {
			byte[] data = FluidDebrisCatalog.packMcmeta().getBytes(StandardCharsets.UTF_8);
			return () -> new ByteArrayInputStream(data);
		}
		return null;
	}

	@Override
	public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
		if (packType != PackType.CLIENT_RESOURCES) {
			return null;
		}
		return resources.get(location);
	}

	@Override
	public void listResources(PackType packType, String namespace, String path, ResourceOutput resourceOutput) {
		if (packType != PackType.CLIENT_RESOURCES) {
			return;
		}
		String prefix = path.isEmpty() ? "" : (path.endsWith("/") ? path : path + "/");
		for (Map.Entry<ResourceLocation, IoSupplier<InputStream>> entry : resources.entrySet()) {
			ResourceLocation id = entry.getKey();
			if (!id.getNamespace().equals(namespace)) {
				continue;
			}
			if (prefix.isEmpty() || id.getPath().startsWith(prefix)) {
				resourceOutput.accept(id, entry.getValue());
			}
		}
	}

	@Override
	public Set<String> getNamespaces(PackType packType) {
		if (packType != PackType.CLIENT_RESOURCES) {
			return Set.of();
		}
		Set<String> namespaces = new HashSet<>();
		for (ResourceLocation id : resources.keySet()) {
			namespaces.add(id.getNamespace());
		}
		namespaces.add("simurail");
		return namespaces;
	}

	@Override
	public void close() {
	}
}
