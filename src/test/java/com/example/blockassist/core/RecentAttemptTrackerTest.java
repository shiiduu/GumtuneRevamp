package com.example.blockassist.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

class RecentAttemptTrackerTest {

	@Test
	void freshAttemptIsRecentlyAttempted() {
		RecentAttemptTracker tracker = new RecentAttemptTracker(1000);
		BlockPos pos = new BlockPos(1, 2, 3);

		tracker.recordAttempt(pos);

		assertTrue(tracker.isRecentlyAttempted(pos));
	}

	@Test
	void expiredAttemptIsForgotten() {
		RecentAttemptTracker tracker = new RecentAttemptTracker(-1); // already-expired window

		BlockPos pos = new BlockPos(1, 2, 3);
		tracker.recordAttempt(pos);

		assertFalse(tracker.isRecentlyAttempted(pos));
	}

	@Test
	void forgetRemovesImmediately() {
		RecentAttemptTracker tracker = new RecentAttemptTracker(60_000);
		BlockPos pos = new BlockPos(0, 0, 0);

		tracker.recordAttempt(pos);
		assertTrue(tracker.isRecentlyAttempted(pos));

		tracker.forget(pos);
		assertFalse(tracker.isRecentlyAttempted(pos));
	}

	@Test
	void untouchedPositionIsNotAttempted() {
		RecentAttemptTracker tracker = new RecentAttemptTracker(1000);
		assertFalse(tracker.isRecentlyAttempted(new BlockPos(9, 9, 9)));
	}
}
