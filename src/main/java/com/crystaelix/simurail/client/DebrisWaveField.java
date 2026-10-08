package com.crystaelix.simurail.client;

import org.joml.Quaternionf;

import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Shared travelling-wave field for floating debris. Height and roll are driven
 * from the same phase so cubes read as one coherent wave, not independent bobbers.
 */
@OnlyIn(Dist.CLIENT)
final class DebrisWaveField {

	static final Vec3 SWELL_DIR = new Vec3(1.0, 0.0, 0.35).normalize();
	private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

	private DebrisWaveField() {
	}

	static Vec3 waveDirection(Vec3 flow) {
		double hx = flow.x;
		double hz = flow.z;
		double len = Math.sqrt(hx * hx + hz * hz);
		if (len < 0.04) {
			return SWELL_DIR;
		}
		return new Vec3(hx / len, 0.0, hz / len);
	}

	static double phase(double x, double z, Vec3 dir, float flowSpeed, float time,
			float jitter, float wavelength, float speedMul) {
		double k = (Math.PI * 2.0) / Math.max(0.5, wavelength);
		double along = x * dir.x + z * dir.z;
		double omega = speedMul * (0.35 + flowSpeed) * 0.4;
		return k * along - omega * time + jitter;
	}

	static double height(double phase, float amplitude, float flowSpeed) {
		double stillScale = flowSpeed < 0.04 ? 0.4 : 0.55 + Math.min(flowSpeed, 1.5) * 0.45;
		return amplitude * stillScale * Math.sin(phase);
	}

	static void rollRotation(Quaternionf dest, Vec3 dir, double phase, float spinMul) {
		Vec3 axis = dir.cross(UP);
		double axisLen = axis.length();
		if (axisLen < 1.0e-4) {
			axis = new Vec3(0.0, 0.0, 1.0);
		} else {
			axis = axis.scale(1.0 / axisLen);
		}
		float angle = (float) phase * spinMul;
		// Extra tumble on the descending back of the wave.
		if (Math.cos(phase) < 0) {
			angle += (float) (-Math.cos(phase) * 0.35 * spinMul);
		}
		dest.rotationAxis(angle, (float) axis.x, (float) axis.y, (float) axis.z);
	}

	static double[] slotOffsets(int slot, int count) {
		return switch (count) {
			case 2 -> slot == 0 ? new double[] {0.33, 0.50} : new double[] {0.67, 0.50};
			case 3 -> switch (slot) {
				case 0 -> new double[] {0.30, 0.32};
				case 1 -> new double[] {0.70, 0.32};
				default -> new double[] {0.50, 0.70};
			};
			case 4 -> new double[] {0.30 + (slot & 1) * 0.40, 0.30 + (slot >> 1) * 0.40};
			default -> new double[] {0.50, 0.50};
		};
	}

	static int cubesForDensity(float density, net.minecraft.util.RandomSource rng) {
		int base = (int) Math.floor(density);
		float frac = density - base;
		int extra = rng.nextFloat() < frac ? 1 : 0;
		return Math.min(4, Math.max(0, base + extra));
	}
}
