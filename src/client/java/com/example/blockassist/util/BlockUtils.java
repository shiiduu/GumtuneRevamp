package com.example.blockassist.util;

import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Static block-state predicates shared by the scanner and crop feature. */
public final class BlockUtils {
	private BlockUtils() {
	}

	public static Identifier idOf(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock());
	}

	public static boolean isBlacklisted(BlockState state, Set<String> blacklist) {
		return !blacklist.isEmpty() && blacklist.contains(idOf(state).toString());
	}

	public static boolean isWhitelisted(BlockState state, Set<String> whitelist) {
		return whitelist.isEmpty() || whitelist.contains(idOf(state).toString());
	}

	public static boolean isMatureCrop(BlockState state) {
		return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
	}
}
