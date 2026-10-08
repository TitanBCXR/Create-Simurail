package com.crystaelix.simurail.client.fluid;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.BufferBuilder;

/**
 * Represents a mesh for rendering fluid elements.
 * Contains vertex data optimized for batched rendering.
 */
public class FluidMesh {
	
	private final float[] vertices; // x, y, z per vertex
	private final int[] indices; // Triangle indices
	private final int triangleCount;
	
	public FluidMesh(float[] vertices, int[] indices) {
		this.vertices = vertices;
		this.indices = indices;
		this.triangleCount = indices.length / 3;
	}
	
	/**
	 * Render this mesh at the given position with the given color.
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
	
	public int getTriangleCount() {
		return triangleCount;
	}
	
	/**
	 * Create a cube mesh (default).
	 */
	public static FluidMesh createCube() {
		float[] vertices = {
			// Back face
			-0.5f, -0.5f, -0.5f,
			 0.5f, -0.5f, -0.5f,
			 0.5f,  0.5f, -0.5f,
			-0.5f,  0.5f, -0.5f,
			// Front face
			-0.5f, -0.5f,  0.5f,
			 0.5f, -0.5f,  0.5f,
			 0.5f,  0.5f,  0.5f,
			-0.5f,  0.5f,  0.5f,
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
		
		return new FluidMesh(vertices, indices);
	}
	
	/**
	 * Create a droplet mesh (low-poly water drop).
	 */
	public static FluidMesh createDroplet() {
		float[] vertices = {
			// Top point
			0.0f, 0.5f, 0.0f,
			// Upper ring (4 verts)
			0.3f, 0.2f, 0.0f,
			0.0f, 0.2f, 0.3f,
			-0.3f, 0.2f, 0.0f,
			0.0f, 0.2f, -0.3f,
			// Lower ring (4 verts)
			0.4f, -0.2f, 0.0f,
			0.0f, -0.2f, 0.4f,
			-0.4f, -0.2f, 0.0f,
			0.0f, -0.2f, -0.4f,
			// Bottom point
			0.0f, -0.5f, 0.0f,
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
		
		return new FluidMesh(vertices, indices);
	}
	
	/**
	 * Create a flat tile mesh (minimal geometry).
	 */
	public static FluidMesh createTile() {
		float[] vertices = {
			-0.5f, 0.0f, -0.5f,
			 0.5f, 0.0f, -0.5f,
			 0.5f, 0.0f,  0.5f,
			-0.5f, 0.0f,  0.5f,
		};
		
		int[] indices = {
			0, 1, 2,
			0, 2, 3,
		};
		
		return new FluidMesh(vertices, indices);
	}
	
	/**
	 * Decimate mesh to fit within triangle limit.
	 */
	public static FluidMesh decimate(FluidMesh mesh, int maxTriangles) {
		if (mesh.triangleCount <= maxTriangles) {
			return mesh;
		}
		
		// Simple decimation: keep every Nth triangle
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
		
		return new FluidMesh(mesh.vertices, newIndices);
	}
}
