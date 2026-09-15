package com.example.blockassist.core;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

import com.example.blockassist.util.RotationUtils;

class TargetSelectorTest {
	private final TargetSelector selector = new TargetSelector();
	private static final Vec3 EYE = Vec3.ZERO;

	private static Target at(int x, int y, int z) {
		BlockPos pos = new BlockPos(x, y, z);
		return new Target(pos, null, Direction.UP, Vec3.atCenterOf(pos));
	}

	private static float yawTo(Target target) {
		return RotationUtils.yawTo(EYE, target.hitPoint());
	}

	@Test
	void emptyCandidateListSelectsNothing() {
		assertNull(selector.select(List.of(), EYE, 0, 0, SelectionMode.CLOSEST));
	}

	@Test
	void closestPicksTheNearestCandidateRegardlessOfRotation() {
		Target near = at(1, 0, 0);
		Target far = at(10, 0, 0);

		// Facing directly at "far" - CLOSEST must still pick "near".
		Target selected = selector.select(List.of(far, near), EYE, yawTo(far), 0, SelectionMode.CLOSEST);

		assertSame(near, selected);
	}

	@Test
	void easiestPrefersLessRotationOverDistance() {
		Target aligned = at(10, 0, 0); // straight ahead, far
		Target behind = at(1, 0, -1); // very close, but behind the player

		Target selected = selector.select(List.of(aligned, behind), EYE, yawTo(aligned), 0, SelectionMode.EASIEST);

		assertSame(aligned, selected);
	}

	@Test
	void easiestFallsBackToDistanceWhenRotationTied() {
		// Exact same direction from the eye (not just the same block-grid axis,
		// which - due to BlockPos#getCenter's +0.5/+0.5/+0.5 offset - would not
		// actually be perfectly collinear) so rotation cost ties exactly and
		// distance must break the tie.
		Vec3 direction = new Vec3(1, 0, 0);
		Target near = new Target(new BlockPos(2, 0, 0), null, Direction.UP, direction.scale(2));
		Target far = new Target(new BlockPos(5, 0, 0), null, Direction.UP, direction.scale(5));

		Target selected = selector.select(List.of(far, near), EYE, yawTo(far), 0, SelectionMode.EASIEST);

		assertSame(near, selected);
	}
}
