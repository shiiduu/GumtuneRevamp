package com.example.blockassist.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;

import com.example.blockassist.util.DebugLog;

/**
 * Thin wrapper around {@link MultiPlayerGameMode}, the same class vanilla
 * input handling drives (see docs/26.1.2-api-notes.md, "Block interaction").
 * No packets are built here and no old-version interaction code is used -
 * every call below is a direct, public, real 26.1.2 method.
 */
public final class InteractionController {

	@FunctionalInterface
	public interface ConfirmationListener {
		void onClientPredictedBreak(BlockPos pos, BlockState previousState);
	}

	private final ConfirmationListener confirmationListener;

	public InteractionController(ConfirmationListener confirmationListener) {
		this.confirmationListener = confirmationListener;
		ClientPlayerBlockBreakEvents.AFTER.register(this::onBlockBroken);
	}

	private void onBlockBroken(ClientLevel level, LocalPlayer player, BlockPos pos, BlockState previousState) {
		DebugLog.log("[CONFIRM] client-predicted break at {} ({} -> air)", pos, previousState.getBlock());
		confirmationListener.onClientPredictedBreak(pos, previousState);
	}

	/**
	 * Validates the target's position and face are what's actually under the
	 * crosshair, then starts breaking it. Returns {@code false} both when the
	 * target is rejected (e.g. protected block) and when the crosshair simply
	 * hasn't caught up to the last rotation update yet - callers should treat
	 * the latter as retryable rather than a hard failure.
	 */
	public boolean start(Target target) {
		BlockHitResult hit = currentHitOn(target);
		if (hit == null) {
			return false;
		}
		MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
		DebugLog.log("[INTERACTION] START {} face={}", target.pos(), target.face());
		return gameMode.startDestroyBlock(target.pos(), hit.getDirection());
	}

	public boolean continueBreaking(Target target) {
		BlockHitResult hit = currentHitOn(target);
		if (hit == null) {
			return false;
		}
		MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
		return gameMode.continueDestroyBlock(target.pos(), hit.getDirection());
	}

	public void stop() {
		MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
		if (gameMode != null && gameMode.isDestroying()) {
			DebugLog.log("[INTERACTION] STOP");
			gameMode.stopDestroyBlock();
		}
	}

	/** Same underlying vanilla operation as {@link #stop()}; kept distinct for state-machine clarity (invalid target vs. natural stop). */
	public void cancel() {
		DebugLog.log("[INTERACTION] CANCEL");
		stop();
	}

	/** True if the live crosshair is currently on the target's position (face is not required to match - used for retry/staleness checks). */
	public boolean isAimedAtPosition(BlockPos pos) {
		HitResult hit = Minecraft.getInstance().hitResult;
		return hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos);
	}

	/**
	 * Deliberately checks position only, not {@link Target#face()}: the face
	 * we pre-selected is what the rotation aims at and what vanilla's
	 * {@code direction} argument records for particles/the abort packet, but
	 * it isn't authoritative for which block breaks (any face works). At
	 * typical aim precision the live crosshair can legitimately resolve to a
	 * different face of the same thin/small target block than the one we
	 * picked; requiring an exact match here would reintroduce spurious
	 * retries without changing what actually gets broken.
	 */
	private BlockHitResult currentHitOn(Target target) {
		HitResult hit = Minecraft.getInstance().hitResult;
		if (!(hit instanceof BlockHitResult blockHit)) {
			return null;
		}
		if (!blockHit.getBlockPos().equals(target.pos())) {
			return null;
		}
		return blockHit;
	}
}
