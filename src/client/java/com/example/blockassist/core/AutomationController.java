package com.example.blockassist.core;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.example.blockassist.config.BlockAssistConfig;
import com.example.blockassist.config.ConfigManager;
import com.example.blockassist.features.CropAutomation;
import com.example.blockassist.util.DebugLog;
import com.example.blockassist.util.PlayerUtils;
import com.example.blockassist.util.TimingUtils;

/**
 * Central lifecycle owner. Drives the {@link BreakState} state machine once
 * per client tick, delegating to {@link TargetScanner}, {@link TargetSelector},
 * {@link RotationController} and {@link InteractionController}. Registered
 * on {@code ClientTickEvents.END_CLIENT_TICK} - see docs/26.1.2-api-notes.md
 * ("Scheduling") for why decisions are tick-bound rather than per-frame.
 */
public final class AutomationController {
	private static final long CONFIRMATION_GRACE_MS = 300;
	private static final int MAX_CANDIDATES = 32;

	private final TargetScanner scanner = new TargetScanner();
	private final TargetSelector selector = new TargetSelector();
	private final RotationController rotation = new RotationController();
	private final RecentAttemptTracker attemptTracker;
	private final InteractionStats stats = new InteractionStats();
	private final BreakState state = new BreakState();
	private final TimingUtils decisionGate;
	private final InteractionController interaction;

	private Predicate<BlockState> featureFilter;
	private boolean activeLastTick = false;
	private ClientLevel lastLevel = null;
	// Set synchronously inside interaction.continueBreaking() below, via the
	// ClientPlayerBlockBreakEvents.AFTER callback chain - everything here runs
	// on the client thread, so no synchronization is needed.
	private boolean predictedBreakPending = false;

	public AutomationController() {
		BlockAssistConfig config = ConfigManager.get();
		attemptTracker = new RecentAttemptTracker(config.attemptMemoryMs);
		decisionGate = new TimingUtils(config.actionsPerSecond);
		interaction = new InteractionController(this::onClientPredictedBreak);
	}

	public InteractionStats stats() {
		return stats;
	}

	public BreakState state() {
		return state;
	}

	public void onClientTick(Minecraft client) {
		BlockAssistConfig config = ConfigManager.get();
		decisionGate.setRatePerSecond(config.actionsPerSecond);
		attemptTracker.setMemoryMs(config.attemptMemoryMs);
		featureFilter = config.cropAutomationEnabled ? state -> CropAutomation.isValidTarget(state, config) : null;

		boolean shouldRun = config.enabled && featureFilter != null;
		boolean environmentReady = PlayerUtils.isWorldReady()
				&& PlayerUtils.isPlayerAlive()
				&& !PlayerUtils.isScreenOpen();

		if (!shouldRun || !environmentReady) {
			if (activeLastTick) {
				resetToIdle("automation stopped");
			}
			activeLastTick = false;
			return;
		}

		ClientLevel level = PlayerUtils.level();
		if (level != lastLevel) {
			resetToIdle("world changed");
			lastLevel = level;
		}

		attemptTracker.pruneExpired();
		LocalPlayer player = PlayerUtils.player();

		switch (state.phase()) {
			case IDLE -> tickIdle(level, player, config);
			case TARGETING -> tickTargeting(level, player);
			case ROTATING -> tickRotating(player, config);
			case STARTING -> tickStarting();
			case BREAKING -> tickBreaking(level, player);
			case WAITING_FOR_CONFIRMATION -> tickWaitingForConfirmation(level);
			case COMPLETE, FAILED -> state.reset();
		}

		activeLastTick = true;
	}

	private void tickIdle(ClientLevel level, LocalPlayer player, BlockAssistConfig config) {
		if (!decisionGate.tryAcquire(System.nanoTime())) {
			return;
		}

		List<BlockPos> candidates = scanner.scan(level, player, config, attemptTracker, featureFilter, MAX_CANDIDATES);
		BlockPos target = selector.select(candidates, player, config.selectionMode);
		if (target == null) {
			return;
		}

		BlockState targetState = level.getBlockState(target);
		DebugLog.log("[TARGET] {} @ {}", targetState.getBlock(), target);
		state.beginTargeting(target, targetState);
	}

	private void tickTargeting(ClientLevel level, LocalPlayer player) {
		if (!isTargetStillValid(level)) {
			state.transition(BreakPhase.FAILED);
			return;
		}

		rotation.setTarget(player.getEyePosition(1.0F), state.targetPos().getCenter());
		state.transition(BreakPhase.ROTATING);
	}

	private void tickRotating(LocalPlayer player, BlockAssistConfig config) {
		if (!isTargetStillValid(PlayerUtils.level())) {
			state.transition(BreakPhase.FAILED);
			return;
		}

		rotation.tick(player, config.rotationMode, config.rotationSpeed);
		if (rotation.isComplete(player)) {
			DebugLog.log("[ROTATION] complete yaw={} pitch={}", player.getYRot(), player.getXRot());
			state.transition(BreakPhase.STARTING);
		}
	}

	private void tickStarting() {
		BlockPos pos = state.targetPos();
		stats.recordRequested();
		attemptTracker.recordAttempt(pos);

		if (interaction.start(pos)) {
			state.transition(BreakPhase.BREAKING);
		} else {
			state.transition(BreakPhase.FAILED);
		}
	}

	private void tickBreaking(ClientLevel level, LocalPlayer player) {
		BlockPos pos = state.targetPos();

		if (player.blockPosition().distSqr(pos) > distanceLimitSq(player)) {
			DebugLog.log("[INTERACTION] target out of range, aborting {}", pos);
			interaction.cancel();
			state.transition(BreakPhase.FAILED);
			return;
		}

		if (!isTargetStillValid(level)) {
			interaction.cancel();
			state.transition(BreakPhase.FAILED);
			return;
		}

		predictedBreakPending = false;
		boolean accepted = interaction.continueBreaking(pos);
		if (predictedBreakPending) {
			// ClientPlayerBlockBreakEvents.AFTER fired synchronously inside continueBreaking above.
			stats.recordCompleted();
			state.transition(BreakPhase.WAITING_FOR_CONFIRMATION);
			return;
		}

		if (!accepted) {
			state.transition(BreakPhase.FAILED);
		}
	}

	private void tickWaitingForConfirmation(ClientLevel level) {
		BlockPos pos = state.targetPos();
		BlockState current = level.getBlockState(pos);

		if (current.equals(state.expectedState())) {
			// Reverted back to the pre-break state: the server rejected the client's prediction.
			DebugLog.log("[CONFIRM] reverted at {}, server rejected the break", pos);
			stats.recordFailed();
			state.transition(BreakPhase.FAILED);
			return;
		}

		if (state.millisInPhase() >= CONFIRMATION_GRACE_MS) {
			DebugLog.log("[CONFIRM] {} confirmed", pos);
			stats.recordConfirmed();
			attemptTracker.forget(pos);
			state.transition(BreakPhase.COMPLETE);
		}
	}

	private boolean isTargetStillValid(ClientLevel level) {
		if (level == null || !state.hasTarget()) {
			return false;
		}
		BlockState current = level.getBlockState(state.targetPos());
		return featureFilter != null && featureFilter.test(current);
	}

	private double distanceLimitSq(LocalPlayer player) {
		double reach = player.blockInteractionRange() + 1.0;
		return reach * reach;
	}

	private void onClientPredictedBreak(BlockPos pos, BlockState previousState) {
		if (state.hasTarget() && pos.equals(state.targetPos()) && state.phase() == BreakPhase.BREAKING) {
			predictedBreakPending = true;
		}
	}

	private void resetToIdle(String reason) {
		DebugLog.log("[STATE] reset ({})", reason);
		if (state.phase() == BreakPhase.BREAKING || state.phase() == BreakPhase.STARTING) {
			interaction.cancel();
		}
		rotation.clear();
		state.reset();
	}
}
