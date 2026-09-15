package com.example.blockassist.core;

import net.minecraft.core.Direction;

/**
 * User-facing face preference. Names match what reads well in a config GUI
 * (TOP/BOTTOM) rather than {@link Direction}'s own (UP/DOWN); {@link #toDirection()}
 * maps between them.
 */
public enum FaceMode {
	ANY,
	TOP,
	BOTTOM,
	NORTH,
	SOUTH,
	EAST,
	WEST;

	/** {@code null} for {@link #ANY} - there is no single fixed direction to map to. */
	public Direction toDirection() {
		return switch (this) {
			case ANY -> null;
			case TOP -> Direction.UP;
			case BOTTOM -> Direction.DOWN;
			case NORTH -> Direction.NORTH;
			case SOUTH -> Direction.SOUTH;
			case EAST -> Direction.EAST;
			case WEST -> Direction.WEST;
		};
	}
}
