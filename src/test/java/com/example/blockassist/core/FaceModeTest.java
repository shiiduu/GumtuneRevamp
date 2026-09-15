package com.example.blockassist.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

class FaceModeTest {

	@Test
	void anyMapsToNoFixedDirection() {
		assertNull(FaceMode.ANY.toDirection());
	}

	@Test
	void topAndBottomMapToUpAndDown() {
		assertEquals(Direction.UP, FaceMode.TOP.toDirection());
		assertEquals(Direction.DOWN, FaceMode.BOTTOM.toDirection());
	}

	@Test
	void horizontalFacesMapDirectly() {
		assertEquals(Direction.NORTH, FaceMode.NORTH.toDirection());
		assertEquals(Direction.SOUTH, FaceMode.SOUTH.toDirection());
		assertEquals(Direction.EAST, FaceMode.EAST.toDirection());
		assertEquals(Direction.WEST, FaceMode.WEST.toDirection());
	}
}
