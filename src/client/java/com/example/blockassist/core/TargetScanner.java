package com.example.blockassist.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.example.blockassist.config.BlockAssistConfig;
import com.example.blockassist.util.BlockUtils;
import com.example.blockassist.util.RaycastUtils;
import com.example.blockassist.util.RotationUtils;

/**
 * Finds candidate interactions around the player. Only the configured
 * bounding box is walked (never a large or unbounded scan), and every
 * candidate is rejected as early and cheaply as possible before the more
 * expensive checks (block state lookup, face raycasts) run.
 */
public final class TargetScanner {
	private static final Direction[] ALL_FACES = Direction.values();

	/**
	 * @param featureFilter feature-specific acceptance test (e.g. "is a mature crop"); {@code null} accepts any non-air, non-blacklisted block
	 */
	public List<Target> scan(
			ClientLevel level,
			LocalPlayer player,
			BlockAssistConfig config,
			RecentAttemptTracker attemptTracker,
			Predicate<BlockState> featureFilter,
			int maxCandidates
	) {
		List<Target> results = new ArrayList<>(maxCandidates);
		BlockPos origin = player.blockPosition();
		int range = config.range;
		int vRange = config.verticalRange;
		long rangeSq = (long) range * range;

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int dy = -vRange; dy <= vRange && results.size() < maxCandidates; dy++) {
			for (int dx = -range; dx <= range && results.size() < maxCandidates; dx++) {
				for (int dz = -range; dz <= range && results.size() < maxCandidates; dz++) {
					if ((long) dx * dx + (long) dz * dz > rangeSq) {
						continue;
					}

					cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);

					if (attemptTracker.isRecentlyAttempted(cursor)) {
						continue;
					}

					BlockState state = level.getBlockState(cursor);
					if (state.isAir()) {
						continue;
					}
					if (BlockUtils.isBlacklisted(state, config.blockBlacklist)) {
						continue;
					}
					if (!BlockUtils.isWhitelisted(state, config.blockWhitelist)) {
						continue;
					}
					if (featureFilter != null && !featureFilter.test(state)) {
						continue;
					}

					BlockPos pos = cursor.immutable();
					Target target = selectFace(level, player, pos, state, config);
					if (target != null) {
						results.add(target);
					}
				}
			}
		}

		return results;
	}

	/** Picks the face to aim at for one candidate position, or {@code null} if no acceptable face exists. */
	private Target selectFace(ClientLevel level, LocalPlayer player, BlockPos pos, BlockState state, BlockAssistConfig config) {
		Direction required = config.faceMode.toDirection();

		if (required != null) {
			// A specific face was configured - only accept it, never fall back to another one.
			if (config.lineOfSight && !RaycastUtils.isFaceUsable(level, player, pos, required)) {
				return null;
			}
			return new Target(pos, state, required, RaycastUtils.faceHitPoint(pos, required));
		}

		// ANY: pick the nearest-to-aim usable face.
		if (config.lineOfSight) {
			return nearestUsableFace(level, player, pos, state);
		}
		return nearestGeometricFace(player, pos, state);
	}

	private Target nearestUsableFace(ClientLevel level, LocalPlayer player, BlockPos pos, BlockState state) {
		Vec3 eyePos = player.getEyePosition(1.0F);
		float currentYaw = player.getYRot();
		float currentPitch = player.getXRot();

		Direction bestFace = null;
		double bestAngle = Double.MAX_VALUE;
		for (Direction face : ALL_FACES) {
			if (!RaycastUtils.isFaceUsable(level, player, pos, face)) {
				continue;
			}
			Vec3 hitPoint = RaycastUtils.faceHitPoint(pos, face);
			double angle = angleToPoint(eyePos, currentYaw, currentPitch, hitPoint);
			if (angle < bestAngle) {
				bestAngle = angle;
				bestFace = face;
			}
		}

		return bestFace == null ? null : new Target(pos, state, bestFace, RaycastUtils.faceHitPoint(pos, bestFace));
	}

	/** No-raycast fallback used when {@code lineOfSight} is disabled: picks the face whose outward normal points most toward the player. */
	private Target nearestGeometricFace(LocalPlayer player, BlockPos pos, BlockState state) {
		Vec3 toPlayer = player.getEyePosition(1.0F).subtract(Vec3.atCenterOf(pos)).normalize();
		Direction bestFace = nearestFacingDirection(toPlayer);
		return new Target(pos, state, bestFace, RaycastUtils.faceHitPoint(pos, bestFace));
	}

	/**
	 * Pure geometry, no world/player access needed: which of the 6 axis
	 * directions has the outward normal most aligned with {@code towardPlayer}
	 * (need not be normalized). Package-private so it's directly unit-testable.
	 */
	static Direction nearestFacingDirection(Vec3 towardPlayer) {
		Direction bestFace = Direction.UP;
		double bestDot = -Double.MAX_VALUE;
		for (Direction face : ALL_FACES) {
			double dot = face.getStepX() * towardPlayer.x + face.getStepY() * towardPlayer.y + face.getStepZ() * towardPlayer.z;
			if (dot > bestDot) {
				bestDot = dot;
				bestFace = face;
			}
		}
		return bestFace;
	}

	private double angleToPoint(Vec3 eyePos, float currentYaw, float currentPitch, Vec3 point) {
		float yaw = RotationUtils.yawTo(eyePos, point);
		float pitch = RotationUtils.pitchTo(eyePos, point);
		return RotationUtils.angleBetween(currentYaw, currentPitch, yaw, pitch);
	}
}
