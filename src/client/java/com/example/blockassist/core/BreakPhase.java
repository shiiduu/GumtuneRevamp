package com.example.blockassist.core;

public enum BreakPhase {
	IDLE,
	TARGETING,
	ROTATING,
	STARTING,
	BREAKING,
	/**
	 * Not entered by the default flow: confirmation is tracked asynchronously
	 * by {@link ConfirmationQueue} instead of blocking this state machine.
	 * Kept for completeness/future use - see docs/architecture.md
	 * ("Decoupled confirmation").
	 */
	WAITING_FOR_CONFIRMATION,
	COMPLETE,
	FAILED
}
