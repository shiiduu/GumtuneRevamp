package com.example.blockassist.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Pure math for aiming: computing the yaw/pitch needed to look from one point
 * at another, and measuring/stepping angular distance. No Minecraft state is
 * read or written here.
 */
public final class RotationUtils {
	private RotationUtils() {
	}

	public static float yawTo(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		return Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F);
	}

	public static float pitchTo(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double dz = to.z - from.z;
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
		return Mth.wrapDegrees((float) (-(Mth.atan2(dy, horizontalDistance) * (180.0 / Math.PI))));
	}

	/** Total angular distance (yaw + pitch, in degrees) between two rotations. */
	public static float angleBetween(float yawA, float pitchA, float yawB, float pitchB) {
		float yawDiff = Mth.degreesDifferenceAbs(yawA, yawB);
		float pitchDiff = Mth.degreesDifferenceAbs(pitchA, pitchB);
		return yawDiff + pitchDiff;
	}

	/** Steps `current` toward `target` by at most `maxDelta` degrees, wrapped correctly. */
	public static float stepTowards(float current, float target, float maxDelta) {
		float diff = Mth.degreesDifference(current, target);
		float clamped = Mth.clamp(diff, -maxDelta, maxDelta);
		return Mth.wrapDegrees(current + clamped);
	}
}
