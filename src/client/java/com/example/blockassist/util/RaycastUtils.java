package com.example.blockassist.util;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Line-of-sight checks against arbitrary candidate positions, independent of
 * where the player is currently looking. Uses the same {@code Level#clip}
 * mechanism vanilla uses for block interaction raycasts.
 */
public final class RaycastUtils {
	private RaycastUtils() {
	}

	/** True if nothing solid blocks a straight line from the entity's eyes to the block's center. */
	public static boolean hasLineOfSight(ClientLevel level, Entity from, BlockPos target) {
		Vec3 eyePos = from.getEyePosition(1.0F);
		Vec3 targetPos = Vec3.atCenterOf(target);

		ClipContext context = new ClipContext(eyePos, targetPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, from);
		BlockHitResult result = level.clip(context);

		return result.getType() != HitResult.Type.BLOCK || result.getBlockPos().equals(target);
	}
}
