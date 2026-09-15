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
 *
 * <p><b>Decoupled confirmation:</b> as soon as a break is predicted
 * (BREAKING), the target is handed off to {@link ConfirmationQueue} and the
 * state machine returns to {@code IDLE} immediately - it does not block the
 * pipeline waiting for the confirmation grace period the way earlier
 * versions did. Rotation/targeting/breaking themselves stay strictly
 * sequential (the player has exactly one camera), but confirmation-watching
 * for several recently-broken targets now happens in parallel with pursuing
 * the next one. See docs/architecture.md for the full reasoning, including
 * why full multi-target pipelining (aiming at target B while still
 * "breaking" target A) was considered and rejected.
 */
public final class AutomationController {
	private static final int MAX_CANDIDATES = 32;

	private final TargetScanner scanner = new TargetScanner();
	private final TargetSelector selector = new TargetSelector();
	private final RotationController rotation = new RotationController();
	private final RecentAttemptTracker attemptTracker;
	private final InteractionStats stats = new InteractionStats();
	private final ConfirmationQueue confirmationQueue = new ConfirmationQueue();
	private final BreakState state = new BreakState();
	private final TimingUtils decisionGate;
	private final InteractionController interaction;

	private Predicate<BlockState> featureFilter;
	private boolean activeLastTick = false;
	private ClientLevel lastLevel = null;
	private long lastStartAttemptAtMillis = 0;
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

	public int pendingConfirmations() {
		return confirmationQueue.size();
	}

	/** False when the master switch is on but no feature (e.g. crop automation) is - i.e. "enabled" but structurally unable to do anything. */
	public boolean hasActiveFeature() {
		return featureFilter != null;
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
		confirmationQueue.tick(level, stats, attemptTracker, config.confirmationTimeoutMs);

		LocalPlayer player = PlayerUtils.player();

		switch (state.phase()) {
			case IDLE -> tickIdle(level, player, config);
			case TARGETING -> tickTargeting(level, player);
			case ROTATING -> tickRotating(player, config);
			case STARTING -> tickStarting(config);
			case BREAKING -> tickBreaking(level, player, config);
			// Confirmation is handled by ConfirmationQueue above, decoupled from
			// this state machine - see the class doc. This phase is not entered
			// by the default flow but is handled defensively in case it ever is.
			case WAITING_FOR_CONFIRMATION -> state.transition(BreakPhase.COMPLETE);
			case COMPLETE, FAILED -> state.reset();
		}

		activeLastTick = true;
	}

	private void tickIdle(ClientLevel level, LocalPlayer player, BlockAssistConfig config) {
		if (!decisionGate.tryAcquire(System.nanoTime())) {
			return;
		}

		long scanStartedAt = System.nanoTime();
		List<Target> candidates = scanner.scan(level, player, config, attemptTracker, featureFilter, MAX_CANDIDATES);
		Target target = selector.select(candidates, player, config.selectionMode);
		stats.recordPhaseDuration("SCAN", (System.nanoTime() - scanStartedAt) / 1_000_000);

		if (target == null) {
			return;
		}

		DebugLog.log("[TARGET] {} @ {} face={}", target.state().getBlock(), target.pos(), target.face());
		stats.recordAcquired();
		attemptTracker.recordAttempt(target.pos());
		state.beginTargeting(target);
	}

	private void tickTargeting(ClientLevel level, LocalPlayer player) {
		if (!isTargetStillValid(level, state.target())) {
			stats.recordFailedBeforeRequest();
			state.transition(BreakPhase.FAILED);
			return;
		}

		rotation.setTarget(player.getEyePosition(1.0F), state.target().hitPoint());
		state.transition(BreakPhase.ROTATING);
	}

	private void tickRotating(LocalPlayer player, BlockAssistConfig config) {
		if (!isTargetStillValid(PlayerUtils.level(), state.target())) {
			stats.recordFailedBeforeRequest();
			state.transition(BreakPhase.FAILED);
			return;
		}

		rotation.tick(player, config.rotationMode, config.rotationSpeed);
		if (rotation.isComplete(player)) {
			DebugLog.log("[ROTATION] complete yaw={} pitch={}", player.getYRot(), player.getXRot());
			stats.recordPhaseDuration("ROTATING", state.millisInPhase());
			state.transition(BreakPhase.STARTING);
		}
	}

	private void tickStarting(BlockAssistConfig config) {
		long now = System.currentTimeMillis();
		if (state.startAttempts() > 0 && now - lastStartAttemptAtMillis < config.retryDelayMs) {
			// Waiting out the retry delay - not a new attempt yet.
			return;
		}

		Target target = state.target();
		lastStartAttemptAtMillis = now;
		int attemptNumber = state.incrementStartAttempts();

		if (interaction.start(target)) {
			stats.recordRequested();
			stats.recordPhaseDuration("STARTING", state.millisInPhase());
			state.transition(BreakPhase.BREAKING);
			return;
		}

		DebugLog.log("[INTERACTION] start miss on {} (attempt {}/{})", target.pos(), attemptNumber, config.maxAttemptsPerTarget);
		if (attemptNumber >= config.maxAttemptsPerTarget) {
			stats.recordFailedBeforeRequest();
			state.transition(BreakPhase.FAILED);
		}
	}

	private void tickBreaking(ClientLevel level, LocalPlayer player, BlockAssistConfig config) {
		Target target = state.target();

		if (state.millisInPhase() > config.breakTimeoutMs) {
			DebugLog.log("[INTERACTION] break timeout on {}", target.pos());
			interaction.cancel();
			stats.recordFailedAfterRequest();
			state.transition(BreakPhase.FAILED);
			return;
		}

		if (player.blockPosition().distSqr(target.pos()) > distanceLimitSq(player)) {
			DebugLog.log("[INTERACTION] target out of range, aborting {}", target.pos());
			interaction.cancel();
			stats.recordFailedAfterRequest();
			state.transition(BreakPhase.FAILED);
			return;
		}

		if (!isTargetStillValid(level, target)) {
			interaction.cancel();
			stats.recordFailedAfterRequest();
			state.transition(BreakPhase.FAILED);
			return;
		}

		predictedBreakPending = false;
		boolean accepted = interaction.continueBreaking(target);
		if (predictedBreakPending) {
			// ClientPlayerBlockBreakEvents.AFTER fired synchronously inside continueBreaking above.
			stats.recordCompleted();
			stats.recordPhaseDuration("BREAKING", state.millisInPhase());
			confirmationQueue.enqueue(target.pos(), target.state());
			state.transition(BreakPhase.COMPLETE);
			return;
		}

		if (!accepted) {
			stats.recordFailedAfterRequest();
			state.transition(BreakPhase.FAILED);
		}
	}

	private boolean isTargetStillValid(ClientLevel level, Target target) {
		if (level == null || target == null) {
			return false;
		}
		BlockState current = level.getBlockState(target.pos());
		return featureFilter != null && featureFilter.test(current);
	}

	private double distanceLimitSq(LocalPlayer player) {
		double reach = player.blockInteractionRange() + 1.0;
		return reach * reach;
	}

	private void onClientPredictedBreak(BlockPos pos, BlockState previousState) {
		Target target = state.target();
		if (target != null && pos.equals(target.pos()) && state.phase() == BreakPhase.BREAKING) {
			predictedBreakPending = true;
		}
	}

	private void resetToIdle(String reason) {
		DebugLog.log("[STATE] reset ({})", reason);
		switch (state.phase()) {
			case TARGETING, ROTATING, STARTING -> stats.discardAcquired();
			case BREAKING -> {
				interaction.cancel();
				stats.discardRequest();
			}
			default -> {
			}
		}
		rotation.clear();
		state.reset();
	}
}
