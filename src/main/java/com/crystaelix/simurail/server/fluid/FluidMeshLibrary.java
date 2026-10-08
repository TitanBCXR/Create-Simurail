package com.crystaelix.simurail.server.fluid;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.crystaelix.simurail.Simurail;
import com.crystaelix.simurail.client.fluid.FluidMesh;
import com.crystaelix.simurail.config.SimurailConfig;

/**
 * Server-side fluid mesh library manager.
 * Loads and validates custom meshes from config/simurail/fluid_meshes/
 */
public class FluidMeshLibrary {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Simurail.MOD_ID + "/FluidMeshLibrary");
	private static final Map<String, FluidMeshData> meshLibrary = new HashMap<>();
	private static boolean initialized = false;
	
	public static class FluidMeshData {
		public final String id;
		public final String displayName;
		public final float[] vertices; // x,y,z per vertex
		public final int[] indices; // triangle indices
		public final int triangleCount;
		
		public FluidMeshData(String id, String displayName, float[] vertices, int[] indices) {
			this.id = id;
			this.displayName = displayName;
			this.vertices = vertices;
			this.indices = indices;
			this.triangleCount = indices.length / 3;
		}
	}
	
	/**
	 * Initialize and load the mesh library.
	 */
	public static void init(Path configDir) {
		if (initialized) {
			return;
		}
		initialized = true;
		
		// Add built-in meshes
		registerBuiltIn("cube", "Cube", FluidMesh.createCube());
		registerBuiltIn("droplet", "Droplet", FluidMesh.createDroplet());
		registerBuiltIn("tile", "Tile", FluidMesh.createTile());
		
		// Load custom meshes from config directory
		Path meshDir = configDir.resolve("simurail").resolve("fluid_meshes");
		if (!Files.exists(meshDir)) {
			try {
				Files.createDirectories(meshDir);
				createReadme(meshDir);
			} catch (IOException e) {
				LOGGER.warn("Failed to create fluid_meshes directory", e);
			}
		}
		
		if (Files.exists(meshDir)) {
			loadCustomMeshes(meshDir);
		}
		
		LOGGER.info("Loaded {} fluid meshes", meshLibrary.size());
	}
	
	/**
	 * Reload the mesh library (custom meshes only, built-ins stay).
	 */
	public static void reload(Path configDir) {
		// Remove custom meshes (keep built-ins)
		meshLibrary.entrySet().removeIf(entry -> 
			!entry.getKey().equals("cube") && 
			!entry.getKey().equals("droplet") && 
			!entry.getKey().equals("tile")
		);
		
		// Reload custom meshes
		Path meshDir = configDir.resolve("simurail").resolve("fluid_meshes");
		if (Files.exists(meshDir)) {
			loadCustomMeshes(meshDir);
		}
		
		LOGGER.info("Reloaded fluid mesh library: {} total meshes", meshLibrary.size());
	}
	
	private static void registerBuiltIn(String id, String name, FluidMesh mesh) {
		meshLibrary.put(id, new FluidMeshData(
			id, name,
			extractVertices(mesh),
			extractIndices(mesh)
		));
	}
	
	// Helper to extract vertices from FluidMesh via reflection or recreation
	private static float[] extractVertices(FluidMesh mesh) {
		// For built-ins, we know the structure - recreate
		if (mesh == FluidMesh.createCube()) {
			return new float[] {
				-0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f,
				-0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
			};
		}
		// Default empty
		return new float[0];
	}
	
	private static int[] extractIndices(FluidMesh mesh) {
		// For built-ins, we know the structure
		if (mesh == FluidMesh.createCube()) {
			return new int[] {
				0, 1, 2, 0, 2, 3,
				4, 6, 5, 4, 7, 6,
				0, 3, 7, 0, 7, 4,
				1, 5, 6, 1, 6, 2,
				0, 4, 5, 0, 5, 1,
				3, 2, 6, 3, 6, 7
			};
		}
		return new int[0];
	}
	
	private static void loadCustomMeshes(Path meshDir) {
		try {
			Files.list(meshDir).forEach(file -> {
				String name = file.getFileName().toString();
				if (name.endsWith(".obj")) {
					loadObjMesh(file);
				} else if (name.endsWith(".json")) {
					loadJsonMesh(file);
				}
			});
		} catch (IOException e) {
			LOGGER.warn("Failed to load custom meshes", e);
		}
	}
	
	private static void loadObjMesh(Path file) {
		String id = file.getFileName().toString().replace(".obj", "");
		try {
			List<float[]> vertices = new ArrayList<>();
			List<Integer> indices = new ArrayList<>();
			
			BufferedReader reader = Files.newBufferedReader(file);
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("v ")) {
					String[] parts = line.split("\\s+");
					if (parts.length >= 4) {
						vertices.add(new float[] {
							Float.parseFloat(parts[1]),
							Float.parseFloat(parts[2]),
							Float.parseFloat(parts[3])
						});
					}
				} else if (line.startsWith("f ")) {
					String[] parts = line.split("\\s+");
					// Parse face indices (support v, v/vt, v/vt/vn formats)
					for (int i = 1; i < parts.length; i++) {
						String[] vertexData = parts[i].split("/");
						int vertexIndex = Integer.parseInt(vertexData[0]) - 1; // OBJ is 1-indexed
						indices.add(vertexIndex);
					}
					// If quad, triangulate (assumes convex)
					if (parts.length == 5) {
						indices.add(indices.get(indices.size() - 4));
						indices.add(indices.get(indices.size() - 2));
					}
				}
			}
			reader.close();
			
			// Convert to arrays
			float[] vertexArray = new float[vertices.size() * 3];
			for (int i = 0; i < vertices.size(); i++) {
				vertexArray[i * 3] = vertices.get(i)[0];
				vertexArray[i * 3 + 1] = vertices.get(i)[1];
				vertexArray[i * 3 + 2] = vertices.get(i)[2];
			}
			
			int[] indexArray = indices.stream().mapToInt(Integer::intValue).toArray();
			
			validateAndRegister(id, id, vertexArray, indexArray, file);
			
		} catch (Exception e) {
			LOGGER.warn("Failed to load mesh {}: {}", file, e.getMessage());
		}
	}
	
	private static void loadJsonMesh(Path file) {
		LOGGER.warn("JSON block model loading not yet implemented: {}", file);
		// TODO: Parse Minecraft JSON block model format
	}
	
	private static void validateAndRegister(String id, String name, float[] vertices, int[] indices, Path source) {
		int triangleCount = indices.length / 3;
		int maxTriangles = SimurailConfig.server().physics.fluidsMaxMeshTriangles.get();
		
		if (triangleCount > maxTriangles) {
			LOGGER.warn("Mesh {} has {} triangles, exceeds limit of {}. Auto-decimating...",
				id, triangleCount, maxTriangles);
			
			// Simple decimation: keep every Nth triangle
			int keepEvery = (int) Math.ceil((double) triangleCount / maxTriangles);
			int newTriCount = triangleCount / keepEvery;
			int[] newIndices = new int[newTriCount * 3];
			
			int writeIdx = 0;
			for (int i = 0; i < indices.length && writeIdx < newIndices.length; i += 3 * keepEvery) {
				if (i + 2 < indices.length && writeIdx + 2 < newIndices.length) {
					newIndices[writeIdx++] = indices[i];
					newIndices[writeIdx++] = indices[i + 1];
					newIndices[writeIdx++] = indices[i + 2];
				}
			}
			
			indices = newIndices;
			triangleCount = indices.length / 3;
			LOGGER.info("Decimated mesh {} to {} triangles", id, triangleCount);
		}
		
		meshLibrary.put(id, new FluidMeshData(id, name, vertices, indices));
		LOGGER.info("Registered fluid mesh '{}' ({} triangles) from {}", id, triangleCount, source);
	}
	
	public static Map<String, FluidMeshData> getMeshLibrary() {
		return new HashMap<>(meshLibrary);
	}
	
	public static FluidMeshData getMesh(String id) {
		return meshLibrary.get(id);
	}
	
	private static void createReadme(Path meshDir) {
		Path readme = meshDir.resolve("README.txt");
		try {
			Files.writeString(readme,
				"Simurail Fluid Mesh Library\n" +
				"============================\n\n" +
				"Place custom fluid mesh files here (.obj format).\n\n" +
				"Requirements:\n" +
				"- Triangulated or quad meshes only\n" +
				"- Positions required (v x y z)\n" +
				"- UVs optional (vt u v)\n" +
				"- Triangle limit: " + SimurailConfig.server().physics.fluidsMaxMeshTriangles.get() + " (configurable)\n" +
				"- Meshes exceeding the limit are auto-decimated\n\n" +
				"Built-in meshes (always available):\n" +
				"- cube (12 triangles)\n" +
				"- droplet (16 triangles)\n" +
				"- tile (2 triangles)\n\n" +
				"Reload with: /simurail meshes reload\n"
			);
		} catch (IOException e) {
			LOGGER.warn("Failed to create README", e);
		}
	}
}
