package com.example.blockassist.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.example.blockassist.config.BlockAssistConfig;
import com.example.blockassist.util.BlockUtils;
import com.example.blockassist.util.RaycastUtils;

/**
 * Finds candidate block positions around the player. Only the configured
 * bounding box is walked (never a large or unbounded scan), and every
 * candidate is rejected as early and cheaply as possible before the more
 * expensive checks (block state lookup, line-of-sight raycast) run.
 */
public final class TargetScanner {

	/**
	 * @param featureFilter feature-specific acceptance test (e.g. "is a mature crop"); {@code null} accepts any non-air, non-blacklisted block
	 */
	public List<BlockPos> scan(
			ClientLevel level,
			LocalPlayer player,
			BlockAssistConfig config,
			RecentAttemptTracker attemptTracker,
			Predicate<BlockState> featureFilter,
			int maxCandidates
	) {
		List<BlockPos> results = new ArrayList<>(maxCandidates);
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
					if (config.lineOfSight && !RaycastUtils.hasLineOfSight(level, player, cursor)) {
						continue;
					}

					results.add(cursor.immutable());
				}
			}
		}

		return results;
	}
}
