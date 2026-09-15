package com.example.blockassist.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InteractionStatsTest {

	@Test
	void requestedNeverCountsAsConfirmed() {
		InteractionStats stats = new InteractionStats();

		stats.recordAcquired();
		stats.recordRequested();
		stats.recordAcquired();
		stats.recordRequested();
		stats.recordAcquired();
		stats.recordRequested();

		assertEquals(3, stats.requestedTotal());
		assertEquals(0, stats.confirmedTotal());
		assertEquals(0.0, stats.successRatio());
	}

	@Test
	void successRatioReflectsConfirmedOverRequested() {
		InteractionStats stats = new InteractionStats();

		stats.recordAcquired();
		stats.recordRequested();
		stats.recordAcquired();
		stats.recordRequested();
		stats.recordConfirmed();

		assertEquals(0.5, stats.successRatio());
	}

	@Test
	void failedBeforeRequestNeverCountsAsRequested() {
		InteractionStats stats = new InteractionStats();

		stats.recordAcquired();
		stats.recordFailedBeforeRequest();

		assertEquals(0, stats.requestedTotal());
		assertEquals(1, stats.failedTotal());
	}

	@Test
	void failedAndExpiredAreTrackedSeparatelyFromConfirmed() {
		InteractionStats stats = new InteractionStats();

		stats.recordAcquired();
		stats.recordRequested();
		stats.recordFailedAfterRequest();

		stats.recordAcquired();
		stats.recordRequested();
		stats.recordExpired();

		assertEquals(1, stats.failedTotal());
		assertEquals(1, stats.expiredTotal());
		assertEquals(0, stats.confirmedTotal());
	}

	@Test
	void resetClearsAllCountersAndPendingState() {
		InteractionStats stats = new InteractionStats();
		stats.recordAcquired();
		stats.recordRequested();
		stats.recordConfirmed();

		stats.reset();

		assertEquals(0, stats.acquiredTotal());
		assertEquals(0, stats.requestedTotal());
		assertEquals(0, stats.confirmedTotal());
		assertEquals(0.0, stats.averageConfirmationMs());
		assertEquals(0.0, stats.averageTargetToRequestMs());
	}

	@Test
	void phaseDurationsAreTrackedByName() {
		InteractionStats stats = new InteractionStats();
		stats.recordPhaseDuration("ROTATING", 43);
		stats.recordPhaseDuration("CONFIRM", 118);

		assertEquals(43, stats.lastPhaseDurationMs("ROTATING"));
		assertEquals(118, stats.lastPhaseDurationMs("CONFIRM"));
		assertEquals(0, stats.lastPhaseDurationMs("UNKNOWN"));
	}
}
