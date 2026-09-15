package com.example.blockassist.core;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import com.example.blockassist.util.RotationUtils;

/**
 * Rotates the player toward a target. This changes the player's actual
 * {@code yRot}/{@code xRot} - the same fields the renderer and the vanilla
 * raycast read - so what the player looks like they're aiming at is exactly
 * what interaction will target. There is no separate, hidden, or
 * server-only rotation channel; see docs/26.1.2-api-notes.md ("Rotation").
 */
public final class RotationController {
	private static final float COMPLETION_THRESHOLD_DEGREES = 1.0F;

	private float targetYaw;
	private float targetPitch;
	private boolean active;

	public void setTarget(Vec3 eyePos, Vec3 targetCenter) {
		targetYaw = RotationUtils.yawTo(eyePos, targetCenter);
		targetPitch = RotationUtils.pitchTo(eyePos, targetCenter);
		active = true;
	}

	public void clear() {
		active = false;
	}

	public boolean isActive() {
		return active;
	}

	/** Applies one tick of rotation movement toward the current target. */
	public void tick(LocalPlayer player, RotationMode mode, double speedDegreesPerTick) {
		if (!active) {
			return;
		}

		float newYaw;
		float newPitch;
		if (mode == RotationMode.INSTANT) {
			newYaw = targetYaw;
			newPitch = targetPitch;
		} else {
			float step = (float) Math.max(0.1, speedDegreesPerTick);
			newYaw = RotationUtils.stepTowards(player.getYRot(), targetYaw, step);
			newPitch = RotationUtils.stepTowards(player.getXRot(), targetPitch, step);
		}

		player.setYRot(newYaw);
		player.setXRot(newPitch);
		player.setYHeadRot(newYaw);
	}

	public boolean isComplete(LocalPlayer player) {
		if (!active) {
			return false;
		}
		float diff = RotationUtils.angleBetween(player.getYRot(), player.getXRot(), targetYaw, targetPitch);
		return diff <= COMPLETION_THRESHOLD_DEGREES;
	}
}
