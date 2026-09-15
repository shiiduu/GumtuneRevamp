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

	/** Validates the target is what's actually under the crosshair, then starts breaking it. */
	public boolean start(BlockPos expectedPos) {
		BlockHitResult hit = currentHitOn(expectedPos);
		if (hit == null) {
			return false;
		}
		MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
		DebugLog.log("[INTERACTION] START {}", expectedPos);
		return gameMode.startDestroyBlock(expectedPos, hit.getDirection());
	}

	public boolean continueBreaking(BlockPos expectedPos) {
		BlockHitResult hit = currentHitOn(expectedPos);
		if (hit == null) {
			return false;
		}
		MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
		return gameMode.continueDestroyBlock(expectedPos, hit.getDirection());
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

	private BlockHitResult currentHitOn(BlockPos expectedPos) {
		HitResult hit = Minecraft.getInstance().hitResult;
		if (!(hit instanceof BlockHitResult blockHit)) {
			return null;
		}
		if (!blockHit.getBlockPos().equals(expectedPos)) {
			return null;
		}
		return blockHit;
	}
}
