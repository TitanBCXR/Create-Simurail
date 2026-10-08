package com.crystaelix.simurail.client;

import org.joml.Quaternionf;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Per-instance 3-axis tumbling for floating debris.
 * Rotation is stored as prev/current quaternions and interpolated with slerp.
 */
@OnlyIn(Dist.CLIENT)
public final class DebrisMotion {

	private static final float DAMPING = 0.997F;
	private static final float STILL_SPIN = 0.4F;

	public final Quaternionf rotation = new Quaternionf();
	public final Quaternionf prevRotation = new Quaternionf();
	public Vec3 angularVelocity = Vec3.ZERO;
	public float bobPhase;
	public float sizeVariation;

	private DebrisMotion() {
	}

	public static DebrisMotion random(RandomSource rng) {
		DebrisMotion motion = new DebrisMotion();
		randomQuaternion(rng, motion.rotation);
		motion.prevRotation.set(motion.rotation);

		float angSpeed = 0.012F + rng.nextFloat() * 0.028F;
		motion.angularVelocity = new Vec3(
			(rng.nextFloat() - 0.5F) * 2F * angSpeed,
			(rng.nextFloat() - 0.5F) * 2F * angSpeed,
			(rng.nextFloat() - 0.5F) * 2F * angSpeed
		);
		motion.bobPhase = rng.nextFloat() * (float) Math.PI * 2F;
		// Tight size variation so a wave field stays coherent.
		motion.sizeVariation = 0.90F + rng.nextFloat() * 0.20F;
		return motion;
	}

	public void setWaveRotation(Quaternionf wave) {
		prevRotation.set(rotation);
		rotation.set(wave);
	}

	public void tick(float flowSpeed, float spinSpeedMultiplier) {
		prevRotation.set(rotation);
		float spinScale = (STILL_SPIN + flowSpeed) * spinSpeedMultiplier;
		rotation.rotateXYZ(
			(float) angularVelocity.x * spinScale,
			(float) angularVelocity.y * spinScale,
			(float) angularVelocity.z * spinScale
		);
		rotation.normalize();
		angularVelocity = angularVelocity.scale(DAMPING);
	}

	public Quaternionf interpolated(float partialTick, Quaternionf dest) {
		return prevRotation.slerp(rotation, partialTick, dest);
	}

	public float bobOffset(int age, float worldSize) {
		return (float) Math.sin((age + bobPhase) * 0.08) * worldSize * 0.06F;
	}

	private static void randomQuaternion(RandomSource rng, Quaternionf dest) {
		float u1 = rng.nextFloat();
		float u2 = rng.nextFloat() * 2F * (float) Math.PI;
		float u3 = rng.nextFloat() * 2F * (float) Math.PI;
		float sqrt1MinusU1 = (float) Math.sqrt(1F - u1);
		float sqrtU1 = (float) Math.sqrt(u1);
		dest.set(
			sqrt1MinusU1 * (float) Math.sin(u2),
			sqrt1MinusU1 * (float) Math.cos(u2),
			sqrtU1 * (float) Math.sin(u3),
			sqrtU1 * (float) Math.cos(u3)
		);
	}
}
