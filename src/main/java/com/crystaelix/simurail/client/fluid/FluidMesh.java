package com.crystaelix.simurail.client.fluid;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Represents a mesh for rendering fluid elements.
 * Contains vertex data optimized for batched rendering.
 */
public class FluidMesh {
	
	private final float[] vertices; // x, y, z per vertex
	private final float[] uvs; // u, v per vertex (optional, null for color-only)
	private final int[] indices; // Triangle indices
	private final int triangleCount;
	
	public FluidMesh(float[] vertices, int[] indices) {
		this(vertices, null, indices);
	}
	
	public FluidMesh(float[] vertices, float[] uvs, int[] indices) {
		this.vertices = vertices;
		this.uvs = uvs;
		this.indices = indices;
		this.triangleCount = indices.length / 3;
	}
	
	/**
	 * Render this mesh at the given position with the given color (no texture).
	 */
	public void render(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, float scale, int color) {
		int r = (color >> 16) & 0xFF;
		int g = (color >> 8) & 0xFF;
		int b = color & 0xFF;
		int a = (color >> 24) & 0xFF;
		
		// Render triangles
		for (int i = 0; i < indices.length; i += 3) {
			int i0 = indices[i] * 3;
			int i1 = indices[i + 1] * 3;
			int i2 = indices[i + 2] * 3;
			
			// Triangle vertices
			buffer.addVertex(matrix, 
				x + vertices[i0] * scale,
				y + vertices[i0 + 1] * scale,
				z + vertices[i0 + 2] * scale)
				.setColor(r, g, b, a);
			
			buffer.addVertex(matrix,
				x + vertices[i1] * scale,
				y + vertices[i1 + 1] * scale,
				z + vertices[i1 + 2] * scale)
				.setColor(r, g, b, a);
			
			buffer.addVertex(matrix,
				x + vertices[i2] * scale,
				y + vertices[i2 + 1] * scale,
				z + vertices[i2 + 2] * scale)
				.setColor(r, g, b, a);
		}
	}
	
	/**
	 * Render this mesh with texture coordinates, tint color, and lighting.
	 */
	public void renderTextured(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z, float scale, 
	                            TextureAtlasSprite sprite, int color, int light, float u0, float v0) {
		int r = (color >> 16) & 0xFF;
		int g = (color >> 8) & 0xFF;
		int b = color & 0xFF;
		int a = (color >> 24) & 0xFF;
		
		float minU = sprite.getU0();
		float maxU = sprite.getU1();
		float minV = sprite.getV0();
		float maxV = sprite.getV1();
		
		// Render triangles with UVs
		for (int i = 0; i < indices.length; i += 3) {
			for (int j = 0; j < 3; j++) {
				int idx = indices[i + j];
				int vIdx = idx * 3;
				int uvIdx = idx * 2;
				
				float vx = x + vertices[vIdx] * scale;
				float vy = y + vertices[vIdx + 1] * scale;
				float vz = z + vertices[vIdx + 2] * scale;
				
				float u, v;
				if (uvs != null && uvIdx + 1 < uvs.length) {
					// Use scaled UV from sub-cube position
					u = minU + (maxU - minU) * (uvs[uvIdx] + u0);
					v = minV + (maxV - minV) * (uvs[uvIdx + 1] + v0);
				} else {
					u = minU;
					v = minV;
				}
				
				buffer.addVertex(matrix, vx, vy, vz)
					.setColor(r, g, b, a)
					.setUv(u, v)
					.setLight(light)
					.setNormal(0, 1, 0);
			}
		}
	}
	
	public int getTriangleCount() {
		return triangleCount;
	}
	
	/**
	 * Create a cube mesh (default) with UV coordinates.
	 */
	public static FluidMesh createCube() {
		float[] vertices = {
			// Back face (0-3)
			-0.5f, -0.5f, -0.5f,
			 0.5f, -0.5f, -0.5f,
			 0.5f,  0.5f, -0.5f,
			-0.5f,  0.5f, -0.5f,
			// Front face (4-7)
			-0.5f, -0.5f,  0.5f,
			 0.5f, -0.5f,  0.5f,
			 0.5f,  0.5f,  0.5f,
			-0.5f,  0.5f,  0.5f,
		};
		
		float[] uvs = {
			// Back face
			0, 0,  1, 0,  1, 1,  0, 1,
			// Front face
			0, 0,  1, 0,  1, 1,  0, 1,
		};
		
		int[] indices = {
			// Back
			0, 1, 2, 0, 2, 3,
			// Front
			4, 6, 5, 4, 7, 6,
			// Left
			0, 3, 7, 0, 7, 4,
			// Right
			1, 5, 6, 1, 6, 2,
			// Bottom
			0, 4, 5, 0, 5, 1,
			// Top
			3, 2, 6, 3, 6, 7
		};
		
		return new FluidMesh(vertices, uvs, indices);
	}
	
	/**
	 * Create a droplet mesh (low-poly water drop) with UV coordinates.
	 */
	public static FluidMesh createDroplet() {
		float[] vertices = {
			// Top point (0)
			0.0f, 0.5f, 0.0f,
			// Upper ring (1-4)
			0.3f, 0.2f, 0.0f,
			0.0f, 0.2f, 0.3f,
			-0.3f, 0.2f, 0.0f,
			0.0f, 0.2f, -0.3f,
			// Lower ring (5-8)
			0.4f, -0.2f, 0.0f,
			0.0f, -0.2f, 0.4f,
			-0.4f, -0.2f, 0.0f,
			0.0f, -0.2f, -0.4f,
			// Bottom point (9)
			0.0f, -0.5f, 0.0f,
		};
		
		float[] uvs = {
			// Top
			0.5f, 0.5f,
			// Upper ring
			1, 0.5f,  0.5f, 1,  0, 0.5f,  0.5f, 0,
			// Lower ring
			1, 0.5f,  0.5f, 1,  0, 0.5f,  0.5f, 0,
			// Bottom
			0.5f, 0.5f,
		};
		
		int[] indices = {
			// Top cap (4 triangles)
			0, 1, 2,
			0, 2, 3,
			0, 3, 4,
			0, 4, 1,
			// Upper band (4 quads = 8 triangles)
			1, 5, 6, 1, 6, 2,
			2, 6, 7, 2, 7, 3,
			3, 7, 8, 3, 8, 4,
			4, 8, 5, 4, 5, 1,
			// Bottom cap (4 triangles)
			9, 6, 5,
			9, 7, 6,
			9, 8, 7,
			9, 5, 8,
		};
		
		return new FluidMesh(vertices, uvs, indices);
	}
	
	/**
	 * Create a flat tile mesh (minimal geometry) with UV coordinates.
	 */
	public static FluidMesh createTile() {
		float[] vertices = {
			-0.5f, 0.0f, -0.5f,
			 0.5f, 0.0f, -0.5f,
			 0.5f, 0.0f,  0.5f,
			-0.5f, 0.0f,  0.5f,
		};
		
		float[] uvs = {
			0, 0,  1, 0,  1, 1,  0, 1,
		};
		
		int[] indices = {
			0, 1, 2,
			0, 2, 3,
		};
		
		return new FluidMesh(vertices, uvs, indices);
	}
	
	/**
	 * Decimate mesh to fit within triangle limit.
	 */
	public static FluidMesh decimate(FluidMesh mesh, int maxTriangles) {
		if (mesh.triangleCount <= maxTriangles) {
			return mesh;
		}
		
		int keepEvery = (int) Math.ceil((double) mesh.triangleCount / maxTriangles);
		int newTriCount = (mesh.triangleCount + keepEvery - 1) / keepEvery;
		
		int[] newIndices = new int[newTriCount * 3];
		int writeIdx = 0;
		
		for (int i = 0; i < mesh.indices.length; i += 3 * keepEvery) {
			if (writeIdx + 3 <= newIndices.length) {
				newIndices[writeIdx] = mesh.indices[i];
				newIndices[writeIdx + 1] = mesh.indices[i + 1];
				newIndices[writeIdx + 2] = mesh.indices[i + 2];
				writeIdx += 3;
			}
		}
		
		return new FluidMesh(mesh.vertices, mesh.uvs, newIndices);
	}
}
