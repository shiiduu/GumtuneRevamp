package com.example.blockassist.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A candidate interaction, not just a position: which block, which face of
 * it, and the exact point on that face to aim at. {@link RotationController}
 * aims at {@link #hitPoint()} (not the block center), and
 * {@link InteractionController} validates the live crosshair against both
 * {@link #pos()} and {@link #face()}.
 */
public record Target(BlockPos pos, BlockState state, Direction face, Vec3 hitPoint) {
}
