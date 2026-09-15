package com.example.blockassist.util;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Line-of-sight checks against arbitrary candidate positions/faces,
 * independent of where the player is currently looking. Uses the same
 * {@code Level#clip} mechanism vanilla uses for block interaction raycasts.
 */
public final class RaycastUtils {
	/** How far off a face's plane to place the aim point, matching vanilla's own block-center convention. */
	private static final double FACE_OFFSET = 0.5;

	/**
	 * How far past {@code targetPoint} the ray is extended. Vanilla's own
	 * raycasts (e.g. the crosshair pick) always aim through/past a block -
	 * out to full reach distance - rather than stopping exactly at its
	 * surface, which is what makes the shape-intersection test reliable. A
	 * ray that ends precisely on a face's boundary plane is a degenerate
	 * case some intersection tests can miss. Without this, every
	 * face-usability check (and therefore all face-aware target selection)
	 * could silently fail - which is exactly what this fixes.
	 */
	private static final double RAY_OVERSHOOT = 0.01;

	private RaycastUtils() {
	}

	/** Casts from the entity's eyes through {@code targetPoint} (see {@link #RAY_OVERSHOOT}); returns the block hit, or {@code null} on a miss/non-block hit. */
	public static BlockHitResult raycastTo(ClientLevel level, Entity from, Vec3 targetPoint) {
		Vec3 eyePos = from.getEyePosition(1.0F);
		Vec3 toward = targetPoint.subtract(eyePos);
		double length = toward.length();
		Vec3 rayEnd = length < 1.0e-4 ? targetPoint : eyePos.add(toward.scale((length + RAY_OVERSHOOT) / length));

		ClipContext context = new ClipContext(eyePos, rayEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, from);
		BlockHitResult result = level.clip(context);
		return result.getType() == HitResult.Type.BLOCK ? result : null;
	}

	/** The point on {@code pos}'s given face that a click would land on. */
	public static Vec3 faceHitPoint(BlockPos pos, Direction face) {
		Vec3 center = Vec3.atCenterOf(pos);
		return center.add(face.getStepX() * FACE_OFFSET, face.getStepY() * FACE_OFFSET, face.getStepZ() * FACE_OFFSET);
	}

	/**
	 * True if a straight line from the entity's eyes actually lands on the
	 * given face of {@code pos} - i.e. nothing occludes it and the face
	 * isn't just geometrically on the far/hidden side of the block.
	 */
	public static boolean isFaceUsable(ClientLevel level, Entity from, BlockPos pos, Direction face) {
		BlockHitResult result = raycastTo(level, from, faceHitPoint(pos, face));
		return result != null && result.getBlockPos().equals(pos) && result.getDirection() == face;
	}
}
