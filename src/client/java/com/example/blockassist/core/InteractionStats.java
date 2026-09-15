package com.example.blockassist.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Counts, rolling per-second rates, and latencies for every stage of the
 * pipeline. A request is never counted as a confirmed break just because it
 * was sent - "requested" and "confirmed" (and every stage between) are
 * tracked independently and can diverge, because the server is authoritative
 * (see docs/26.1.2-api-notes.md, "Confirmation").
 *
 * <p>Stages, in order: acquired (a target was picked) -&gt; requested (a
 * {@code startDestroyBlock} was actually accepted) -&gt; completed (the
 * client predicted a break locally) -&gt; confirmed/failed/expired (what the
 * world actually did afterwards). Every {@code recordAcquired}/{@code
 * recordRequested} call pushes a matching pending-latency entry that exactly
 * one later call is responsible for popping - see the paired {@code record*}
 * / {@code discard*} methods below. This only needs to be exactly-once, not
 * strictly FIFO-accurate under concurrency, because the automation pursues
 * one target at a time; only confirmations (via {@link ConfirmationQueue})
 * are ever pending for more than one target at once, and confirmations
 * resolve in roughly the order they were enqueued.
 */
public final class InteractionStats {
	private static final long WINDOW_MS = 1000;

	private long acquiredTotal;
	private long requestedTotal;
	private long completedTotal;
	private long confirmedTotal;
	private long failedTotal;
	private long expiredTotal;

	private final Deque<Long> acquiredTimestamps = new ArrayDeque<>();
	private final Deque<Long> requestedTimestamps = new ArrayDeque<>();
	private final Deque<Long> completedTimestamps = new ArrayDeque<>();
	private final Deque<Long> confirmedTimestamps = new ArrayDeque<>();
	private final Deque<Long> failedTimestamps = new ArrayDeque<>();

	// pendingRequestStartMs: one entry per acquired target awaiting its start request.
	// pendingConfirmationStartMs: one entry per sent request awaiting a final outcome.
	private final Deque<Long> pendingRequestStartMs = new ArrayDeque<>();
	private final Deque<Long> pendingConfirmationStartMs = new ArrayDeque<>();
	private double averageTargetToRequestMs = 0.0;
	private double averageConfirmationMs = 0.0;

	private final Map<String, Long> lastPhaseDurationMs = new HashMap<>();

	public void recordAcquired() {
		acquiredTotal++;
		long now = System.currentTimeMillis();
		acquiredTimestamps.addLast(now);
		pendingRequestStartMs.addLast(now);
		prune(acquiredTimestamps, now);
	}

	public void recordRequested() {
		requestedTotal++;
		long now = System.currentTimeMillis();
		requestedTimestamps.addLast(now);
		prune(requestedTimestamps, now);

		Long acquiredAt = pendingRequestStartMs.pollFirst();
		if (acquiredAt != null) {
			averageTargetToRequestMs = smooth(averageTargetToRequestMs, now - acquiredAt);
		}
		pendingConfirmationStartMs.addLast(now);
	}

	public void recordCompleted() {
		completedTotal++;
		long now = System.currentTimeMillis();
		completedTimestamps.addLast(now);
		prune(completedTimestamps, now);
	}

	public void recordConfirmed() {
		confirmedTotal++;
		long now = System.currentTimeMillis();
		confirmedTimestamps.addLast(now);
		prune(confirmedTimestamps, now);

		Long startedAt = pendingConfirmationStartMs.pollFirst();
		if (startedAt != null) {
			averageConfirmationMs = smooth(averageConfirmationMs, now - startedAt);
		}
	}

	/** A target was abandoned before any request was ever sent for it (invalid target, out of attempts, etc.). */
	public void recordFailedBeforeRequest() {
		countFailure();
		pendingRequestStartMs.pollFirst();
	}

	/** A request was sent but the outcome was negative (rejected mid-break, or the world reverted the predicted break). */
	public void recordFailedAfterRequest() {
		countFailure();
		pendingConfirmationStartMs.pollFirst();
	}

	/** A pending confirmation was dropped without ever resolving (e.g. world changed while waiting). */
	public void recordExpired() {
		expiredTotal++;
		pendingConfirmationStartMs.pollFirst();
	}

	/** Supervisory abort (automation disabled, screen opened, ...) before any request was sent - not counted as a failure. */
	public void discardAcquired() {
		pendingRequestStartMs.pollFirst();
	}

	/** Supervisory abort after a request was sent but before it predicted a break - not counted as a failure. */
	public void discardRequest() {
		pendingConfirmationStartMs.pollFirst();
	}

	private void countFailure() {
		failedTotal++;
		long now = System.currentTimeMillis();
		failedTimestamps.addLast(now);
		prune(failedTimestamps, now);
	}

	/** Instantaneous duration (ms) most recently spent in a named phase - e.g. "ROTATING", "CONFIRM". Not averaged/smoothed; matches the HUD's "Rotation: 43 ms" style readout. */
	public void recordPhaseDuration(String phase, long ms) {
		lastPhaseDurationMs.put(phase, ms);
	}

	public long lastPhaseDurationMs(String phase) {
		return lastPhaseDurationMs.getOrDefault(phase, 0L);
	}

	private double smooth(double runningAverage, long sampleMs) {
		return runningAverage == 0.0 ? sampleMs : (runningAverage * 0.9) + (sampleMs * 0.1);
	}

	private void prune(Deque<Long> timestamps, long now) {
		while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_MS) {
			timestamps.pollFirst();
		}
	}

	public double acquiredPerSecond() {
		prune(acquiredTimestamps, System.currentTimeMillis());
		return acquiredTimestamps.size();
	}

	public double requestedPerSecond() {
		prune(requestedTimestamps, System.currentTimeMillis());
		return requestedTimestamps.size();
	}

	public double completedPerSecond() {
		prune(completedTimestamps, System.currentTimeMillis());
		return completedTimestamps.size();
	}

	public double confirmedPerSecond() {
		prune(confirmedTimestamps, System.currentTimeMillis());
		return confirmedTimestamps.size();
	}

	public double failedPerSecond() {
		prune(failedTimestamps, System.currentTimeMillis());
		return failedTimestamps.size();
	}

	public double successRatio() {
		return requestedTotal == 0 ? 0.0 : (double) confirmedTotal / requestedTotal;
	}

	public double averageTargetToRequestMs() {
		return averageTargetToRequestMs;
	}

	public double averageConfirmationMs() {
		return averageConfirmationMs;
	}

	public long acquiredTotal() {
		return acquiredTotal;
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

	public void reset() {
		acquiredTotal = requestedTotal = completedTotal = confirmedTotal = failedTotal = expiredTotal = 0;
		acquiredTimestamps.clear();
		requestedTimestamps.clear();
		completedTimestamps.clear();
		confirmedTimestamps.clear();
		failedTimestamps.clear();
		pendingRequestStartMs.clear();
		pendingConfirmationStartMs.clear();
		averageTargetToRequestMs = 0.0;
		averageConfirmationMs = 0.0;
		lastPhaseDurationMs.clear();
	}
}
