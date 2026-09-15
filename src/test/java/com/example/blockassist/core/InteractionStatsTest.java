package com.example.blockassist.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InteractionStatsTest {

	@Test
	void requestedNeverCountsAsConfirmed() {
		InteractionStats stats = new InteractionStats();

		stats.recordRequested();
		stats.recordRequested();
		stats.recordRequested();

		assertEquals(3, stats.requestedTotal());
		assertEquals(0, stats.confirmedTotal());
		assertEquals(0.0, stats.successRatio());
	}

	@Test
	void successRatioReflectsConfirmedOverRequested() {
		InteractionStats stats = new InteractionStats();

		stats.recordRequested();
		stats.recordRequested();
		stats.recordConfirmed();

		assertEquals(0.5, stats.successRatio());
	}

	@Test
	void failedAndExpiredAreTrackedSeparatelyFromConfirmed() {
		InteractionStats stats = new InteractionStats();

		stats.recordRequested();
		stats.recordFailed();
		stats.recordRequested();
		stats.recordExpired();

		assertEquals(1, stats.failedTotal());
		assertEquals(1, stats.expiredTotal());
		assertEquals(0, stats.confirmedTotal());
	}
}
