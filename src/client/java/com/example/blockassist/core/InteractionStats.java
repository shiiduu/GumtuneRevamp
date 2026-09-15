package com.example.blockassist.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Counts and rolling rates for interaction outcomes. A request is never
 * counted as a confirmed break just because it was sent - "requested" and
 * "confirmed" are tracked independently and can diverge, because the server
 * is authoritative (see docs/26.1.2-api-notes.md, "Confirmation").
 */
public final class InteractionStats {
	private static final long WINDOW_MS = 1000;

	private long requestedTotal;
	private long completedTotal;
	private long confirmedTotal;
	private long failedTotal;
	private long expiredTotal;

	private final Deque<Long> requestedTimestamps = new ArrayDeque<>();
	private final Deque<Long> confirmedTimestamps = new ArrayDeque<>();

	private final Deque<Long> pendingConfirmationStartMs = new ArrayDeque<>();
	private double averageConfirmationMs = 0.0;

	public void recordRequested() {
		requestedTotal++;
		long now = System.currentTimeMillis();
		requestedTimestamps.addLast(now);
		pendingConfirmationStartMs.addLast(now);
		prune(requestedTimestamps, now);
	}

	public void recordCompleted() {
		completedTotal++;
	}

	public void recordConfirmed() {
		confirmedTotal++;
		long now = System.currentTimeMillis();
		confirmedTimestamps.addLast(now);
		prune(confirmedTimestamps, now);

		Long startedAt = pendingConfirmationStartMs.pollFirst();
		if (startedAt != null) {
			long sampleMs = now - startedAt;
			averageConfirmationMs = averageConfirmationMs == 0.0
					? sampleMs
					: (averageConfirmationMs * 0.9) + (sampleMs * 0.1);
		}
	}

	public void recordFailed() {
		failedTotal++;
		pendingConfirmationStartMs.pollFirst();
	}

	public void recordExpired() {
		expiredTotal++;
		pendingConfirmationStartMs.pollFirst();
	}

	private void prune(Deque<Long> timestamps, long now) {
		while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_MS) {
			timestamps.pollFirst();
		}
	}

	public double requestedPerSecond() {
		prune(requestedTimestamps, System.currentTimeMillis());
		return requestedTimestamps.size();
	}

	public double confirmedPerSecond() {
		prune(confirmedTimestamps, System.currentTimeMillis());
		return confirmedTimestamps.size();
	}

	public double successRatio() {
		return requestedTotal == 0 ? 0.0 : (double) confirmedTotal / requestedTotal;
	}

	public double averageConfirmationMs() {
		return averageConfirmationMs;
	}

	public long requestedTotal() {
		return requestedTotal;
	}

	public long confirmedTotal() {
		return confirmedTotal;
	}

	public long failedTotal() {
		return failedTotal;
	}

	public long expiredTotal() {
		return expiredTotal;
	}
}
