package com.example.blockassist.util;

/**
 * A nanosecond-resolution rate gate. Tracks whether at least {@code 1/rate}
 * seconds have elapsed since the last granted call, independent of tick
 * rate. This measures the requested decision cadence only - it does not by
 * itself cause Minecraft interaction calls to run more than once per client
 * tick (see docs/26.1.2-api-notes.md, "Scheduling").
 */
public final class TimingUtils {
	private long lastGrantedNanos = 0L;
	private double ratePerSecond;

	public TimingUtils(double ratePerSecond) {
		setRatePerSecond(ratePerSecond);
	}

	public void setRatePerSecond(double ratePerSecond) {
		this.ratePerSecond = Math.max(0.1, ratePerSecond);
	}

	public boolean tryAcquire(long nowNanos) {
		long intervalNanos = (long) (1_000_000_000L / ratePerSecond);
		if (nowNanos - lastGrantedNanos >= intervalNanos) {
			lastGrantedNanos = nowNanos;
			return true;
		}
		return false;
	}

	public void reset() {
		lastGrantedNanos = 0L;
	}
}
