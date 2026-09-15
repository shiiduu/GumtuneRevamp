package com.example.blockassist.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

class RaycastUtilsTest {

	@Test
	void faceHitPointIsOnTopFaceCenterForUp() {
		Vec3 hit = RaycastUtils.faceHitPoint(new BlockPos(10, 64, -5), Direction.UP);
		assertEquals(10.5, hit.x, 1e-9);
		assertEquals(65.0, hit.y, 1e-9);
		assertEquals(-4.5, hit.z, 1e-9);
	}

	@Test
	void faceHitPointIsOnBottomFaceCenterForDown() {
		Vec3 hit = RaycastUtils.faceHitPoint(new BlockPos(0, 0, 0), Direction.DOWN);
		assertEquals(0.5, hit.x, 1e-9);
		assertEquals(0.0, hit.y, 1e-9);
		assertEquals(0.5, hit.z, 1e-9);
	}

	@Test
	void faceHitPointOffsetsHalfABlockAlongTheFaceAxisOnly() {
		BlockPos pos = new BlockPos(3, 3, 3);
		Vec3 center = Vec3.atCenterOf(pos);
		Vec3 hit = RaycastUtils.faceHitPoint(pos, Direction.NORTH);

		assertEquals(center.x, hit.x, 1e-9);
		assertEquals(center.y, hit.y, 1e-9);
		assertEquals(center.z - 0.5, hit.z, 1e-9);
	}
}
