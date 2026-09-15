package com.example.blockassist.core;

import com.example.blockassist.util.DebugLog;

/**
 * Explicit state machine for a single break attempt. One target at a time;
 * {@link AutomationController} owns the instance and drives transitions. No
 * boolean-flag soup - every phase change goes through {@link #transition(BreakPhase)}.
 *
 * <p>No break-direction is tracked separately from {@link Target#face()}:
 * {@code MultiPlayerGameMode.stopDestroyBlock()} takes no direction argument
 * (it always uses {@code Direction.DOWN} internally), and
 * {@code start}/{@code continueBreaking} re-derive the real face direction
 * from the live crosshair hit result on every call - see
 * {@link InteractionController}.
 */
public final class BreakState {
	private BreakPhase phase = BreakPhase.IDLE;
	private Target target;
	private long phaseStartedAtMillis = System.currentTimeMillis();
	private long targetAcquiredAtMillis;
	private int startAttempts;

	public BreakPhase phase() {
		return phase;
	}

	public Target target() {
		return target;
	}

	public long millisInPhase() {
		return System.currentTimeMillis() - phaseStartedAtMillis;
	}

	public long millisSinceAcquired() {
		return System.currentTimeMillis() - targetAcquiredAtMillis;
	}

	public int startAttempts() {
		return startAttempts;
	}

	public int incrementStartAttempts() {
		return ++startAttempts;
	}

	public void beginTargeting(Target target) {
		this.target = target;
		this.targetAcquiredAtMillis = System.currentTimeMillis();
		this.startAttempts = 0;
		transition(BreakPhase.TARGETING);
	}

	/** Replaces the live target with a freshly re-scanned one at the same position (e.g. after a face re-check), without resetting acquisition timing/attempts. */
	public void updateTarget(Target target) {
		this.target = target;
	}

	public void transition(BreakPhase next) {
		if (next == phase) {
			return;
		}
		DebugLog.log("[STATE] {} -> {} (pos={})", phase, next, target != null ? target.pos() : null);
		phase = next;
		phaseStartedAtMillis = System.currentTimeMillis();
	}

	public void reset() {
		transition(BreakPhase.IDLE);
		target = null;
		startAttempts = 0;
	}

	public boolean hasTarget() {
		return target != null;
	}
}
