package com.example.blockassist.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

/** Covers the no-raycast geometric face fallback used when {@code lineOfSight} is disabled. */
class TargetScannerFaceTest {

	@Test
	void picksUpWhenPlayerIsDirectlyAbove() {
		assertEquals(Direction.UP, TargetScanner.nearestFacingDirection(new Vec3(0, 1, 0)));
	}

	@Test
	void picksNorthWhenPlayerIsToTheNorth() {
		assertEquals(Direction.NORTH, TargetScanner.nearestFacingDirection(new Vec3(0, 0, -1)));
	}

	@Test
	void picksDominantAxisForADiagonalDirection() {
		// Mostly east, slightly up - east should win.
		assertEquals(Direction.EAST, TargetScanner.nearestFacingDirection(new Vec3(0.9, 0.1, 0)));
	}
}
