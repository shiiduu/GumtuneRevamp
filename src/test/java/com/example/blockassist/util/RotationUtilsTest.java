package com.example.blockassist.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RotationUtilsTest {

	@Test
	void angleBetweenIsZeroForIdenticalRotations() {
		assertEquals(0.0F, RotationUtils.angleBetween(90, 10, 90, 10), 0.001F);
	}

	@Test
	void angleBetweenAccountsForYawWrapping() {
		// 179 and -179 are 2 degrees apart, not 358.
		float angle = RotationUtils.angleBetween(179, 0, -179, 0);
		assertTrue(angle <= 2.5F, "expected wrapped angle near 2 degrees, got " + angle);
	}

	@Test
	void stepTowardsDoesNotOvershootWhenCloserThanMaxDelta() {
		float result = RotationUtils.stepTowards(10.0F, 12.0F, 15.0F);
		assertEquals(12.0F, result, 0.001F);
	}

	@Test
	void stepTowardsClampsToMaxDelta() {
		float result = RotationUtils.stepTowards(0.0F, 100.0F, 10.0F);
		assertEquals(10.0F, result, 0.001F);
	}
}
