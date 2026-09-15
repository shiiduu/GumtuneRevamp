package com.example.blockassist.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.core.BlockPos;

/**
 * Remembers positions the client has already tried breaking so
 * {@link TargetScanner}/{@link TargetSelector} don't immediately re-select
 * them while the world hasn't confirmed the change yet. Never a permanent
 * blacklist - entries expire on their own, and are removed immediately once
 * the position's block state actually changes.
 *
 * <p>Client-thread only, like everything else in {@code core} - no internal
 * synchronization is needed or provided.
 */
public final class RecentAttemptTracker {
	private record Attempt(long timestampMillis, int attemptCount) {
	}

	private final Map<BlockPos, Attempt> attempts = new HashMap<>();
	private long memoryMs;

	public RecentAttemptTracker(long memoryMs) {
		this.memoryMs = memoryMs;
	}

	public void setMemoryMs(long memoryMs) {
		this.memoryMs = memoryMs;
	}

	public void recordAttempt(BlockPos pos) {
		BlockPos immutable = pos.immutable();
		attempts.compute(immutable, (key, existing) -> new Attempt(
				System.currentTimeMillis(),
				existing == null ? 1 : existing.attemptCount() + 1
		));
	}

	public boolean isRecentlyAttempted(BlockPos pos) {
		Attempt attempt = attempts.get(pos);
		if (attempt == null) {
			return false;
		}
		if (System.currentTimeMillis() - attempt.timestampMillis() >= memoryMs) {
			attempts.remove(pos);
			return false;
		}
		return true;
	}

	public void forget(BlockPos pos) {
		attempts.remove(pos);
	}

	/** Call once per tick (or scan pass) to drop stale entries without waiting for a lookup. */
	public void pruneExpired() {
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<BlockPos, Attempt>> it = attempts.entrySet().iterator();
		while (it.hasNext()) {
			if (now - it.next().getValue().timestampMillis() >= memoryMs) {
				it.remove();
			}
		}
	}

	public int size() {
		return attempts.size();
	}
}
