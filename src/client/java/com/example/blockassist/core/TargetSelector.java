package com.example.blockassist.core;

import java.util.Comparator;
import java.util.List;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import com.example.blockassist.util.RotationUtils;

/**
 * Picks one target out of the scanner's candidates. Deterministic: the same
 * candidate list and player state always yields the same choice, unless a
 * future randomization option is explicitly enabled (none exists yet).
 */
public final class TargetSelector {

	public BlockPos select(List<BlockPos> candidates, LocalPlayer player, SelectionMode mode) {
		if (candidates.isEmpty()) {
			return null;
		}

		Comparator<BlockPos> comparator = switch (mode) {
			case CLOSEST -> closestComparator(player);
			case EASIEST -> easiestComparator(player);
		};

		return candidates.stream().min(comparator).orElse(null);
	}

	private Comparator<BlockPos> closestComparator(LocalPlayer player) {
		BlockPos playerPos = player.blockPosition();
		return Comparator.comparingDouble(pos -> pos.distSqr(playerPos));
	}

	/**
	 * "Easiest" ranks by required rotation first (all candidates are already
	 * visible/reachable - the scanner only returns line-of-sight-passing,
	 * in-range positions - see TargetScanner), then falls back to distance.
	 * The player's eye position is fixed for the whole selection pass, so it
	 * is computed once here rather than per candidate/per comparison.
	 */
	private Comparator<BlockPos> easiestComparator(LocalPlayer player) {
		BlockPos playerPos = player.blockPosition();
		Vec3 eyePos = player.getEyePosition(1.0F);
		float currentYaw = player.getYRot();
		float currentPitch = player.getXRot();

		return Comparator
				.comparingDouble((BlockPos pos) -> rotationNeeded(eyePos, currentYaw, currentPitch, pos))
				.thenComparingDouble(pos -> pos.distSqr(playerPos));
	}

	private double rotationNeeded(Vec3 eyePos, float currentYaw, float currentPitch, BlockPos target) {
		Vec3 targetCenter = target.getCenter();
		float yaw = RotationUtils.yawTo(eyePos, targetCenter);
		float pitch = RotationUtils.pitchTo(eyePos, targetCenter);
		return RotationUtils.angleBetween(currentYaw, currentPitch, yaw, pitch);
	}
}
