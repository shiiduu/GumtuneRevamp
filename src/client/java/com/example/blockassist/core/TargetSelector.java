package com.example.blockassist.core;

import java.util.Comparator;
import java.util.List;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import com.example.blockassist.util.RotationUtils;

/**
 * Picks one target out of the scanner's candidates. Deterministic: the same
 * candidate list and player state always yields the same choice, unless a
 * future randomization option is explicitly enabled (none exists yet).
 *
 * <p>The actual ranking ({@link #select(List, Vec3, float, float, SelectionMode)})
 * takes plain eye position/rotation rather than a {@link LocalPlayer}
 * directly, so it can be unit-tested without a running game; {@link #select(List, LocalPlayer, SelectionMode)}
 * is the thin wrapper used at runtime.
 */
public final class TargetSelector {

	public Target select(List<Target> candidates, LocalPlayer player, SelectionMode mode) {
		return select(candidates, player.getEyePosition(1.0F), player.getYRot(), player.getXRot(), mode);
	}

	public Target select(List<Target> candidates, Vec3 eyePos, float currentYaw, float currentPitch, SelectionMode mode) {
		if (candidates.isEmpty()) {
			return null;
		}

		Comparator<Target> comparator = switch (mode) {
			case CLOSEST -> closestComparator(eyePos);
			case EASIEST -> easiestComparator(eyePos, currentYaw, currentPitch);
		};

		return candidates.stream().min(comparator).orElse(null);
	}

	private Comparator<Target> closestComparator(Vec3 eyePos) {
		return Comparator.comparingDouble(target -> target.hitPoint().distanceToSqr(eyePos));
	}

	/**
	 * "Easiest" ranks by required rotation first (all candidates are already
	 * visible/reachable - the scanner only returns line-of-sight-passing,
	 * in-range positions - see TargetScanner), then falls back to distance.
	 */
	private Comparator<Target> easiestComparator(Vec3 eyePos, float currentYaw, float currentPitch) {
		return Comparator
				.comparingDouble((Target target) -> rotationNeeded(eyePos, currentYaw, currentPitch, target))
				.thenComparingDouble(target -> target.hitPoint().distanceToSqr(eyePos));
	}

	private double rotationNeeded(Vec3 eyePos, float currentYaw, float currentPitch, Target target) {
		float yaw = RotationUtils.yawTo(eyePos, target.hitPoint());
		float pitch = RotationUtils.pitchTo(eyePos, target.hitPoint());
		return RotationUtils.angleBetween(currentYaw, currentPitch, yaw, pitch);
	}
}
