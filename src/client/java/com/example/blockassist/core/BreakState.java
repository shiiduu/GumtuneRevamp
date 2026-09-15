package com.example.blockassist.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.example.blockassist.util.DebugLog;

/**
 * Explicit state machine for a single break attempt. One target at a time;
 * {@link com.example.blockassist.core.AutomationController} owns the
 * instance and drives transitions. No boolean-flag soup - every phase change
 * goes through {@link #transition(BreakPhase)}.
 *
 * <p>No break-direction is tracked here: {@code MultiPlayerGameMode.stopDestroyBlock()}
 * takes no direction argument (it always uses {@code Direction.DOWN}
 * internally), and {@code start}/{@code continueBreaking} re-derive the real
 * face direction from the live crosshair hit result on every call - see
 * {@link InteractionController}.
 */
public final class BreakState {
	private BreakPhase phase = BreakPhase.IDLE;
	private BlockPos targetPos;
	private BlockState expectedState;
	private long phaseStartedAtMillis = System.currentTimeMillis();

	public BreakPhase phase() {
		return phase;
	}

	public BlockPos targetPos() {
		return targetPos;
	}

	public BlockState expectedState() {
		return expectedState;
	}

	public long millisInPhase() {
		return System.currentTimeMillis() - phaseStartedAtMillis;
	}

	public void beginTargeting(BlockPos pos, BlockState state) {
		this.targetPos = pos;
		this.expectedState = state;
		transition(BreakPhase.TARGETING);
	}

	public void transition(BreakPhase next) {
		if (next == phase) {
			return;
		}
		DebugLog.log("[STATE] {} -> {} (pos={})", phase, next, targetPos);
		phase = next;
		phaseStartedAtMillis = System.currentTimeMillis();
	}

	public void reset() {
		transition(BreakPhase.IDLE);
		targetPos = null;
		expectedState = null;
	}

	public boolean hasTarget() {
		return targetPos != null;
	}
}
