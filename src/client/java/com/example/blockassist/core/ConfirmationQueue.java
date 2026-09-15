package com.example.blockassist.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.example.blockassist.util.DebugLog;

/**
 * Tracks client-predicted breaks awaiting a real answer from the world,
 * independently of {@link BreakState}. This is what lets
 * {@link AutomationController} move on to the next target immediately after
 * a break is predicted, instead of blocking the whole pipeline on a
 * confirmation grace period (see docs/26.1.2-api-notes.md, "Scheduling" and
 * the architecture notes on decoupled confirmation).
 */
public final class ConfirmationQueue {
	private record Pending(BlockPos pos, BlockState expectedOriginal, long startedAtMillis) {
	}

	private final List<Pending> pending = new ArrayList<>();

	public void enqueue(BlockPos pos, BlockState expectedOriginal) {
		pending.add(new Pending(pos, expectedOriginal, System.currentTimeMillis()));
	}

	/** Call once per client tick. Resolves each pending entry as confirmed (still changed after the timeout) or failed (reverted - the server rejected it). */
	public void tick(ClientLevel level, InteractionStats stats, RecentAttemptTracker attemptTracker, long confirmationTimeoutMs) {
		if (pending.isEmpty() || level == null) {
			return;
		}

		long now = System.currentTimeMillis();
		Iterator<Pending> it = pending.iterator();
		while (it.hasNext()) {
			Pending entry = it.next();
			BlockState current = level.getBlockState(entry.pos());

			if (current.equals(entry.expectedOriginal())) {
				DebugLog.log("[CONFIRM] reverted at {}, server rejected the break", entry.pos());
				stats.recordFailedAfterRequest();
				stats.recordPhaseDuration("CONFIRM", now - entry.startedAtMillis());
				it.remove();
				continue;
			}

			if (now - entry.startedAtMillis() >= confirmationTimeoutMs) {
				DebugLog.log("[CONFIRM] {} confirmed", entry.pos());
				stats.recordConfirmed();
				stats.recordPhaseDuration("CONFIRM", now - entry.startedAtMillis());
				attemptTracker.forget(entry.pos());
				it.remove();
			}
		}
	}

	/** Drops every pending entry without resolving it (e.g. automation disabled, world changed) - counted as expired, not confirmed or failed. */
	public void clear(InteractionStats stats) {
		for (int i = 0; i < pending.size(); i++) {
			stats.recordExpired();
		}
		pending.clear();
	}

	public int size() {
		return pending.size();
	}
}
