package com.example.blockassist.features;

import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.example.blockassist.config.BlockAssistConfig;
import com.example.blockassist.util.BlockUtils;

/**
 * Crop-specific target acceptance, kept separate from generic block
 * interaction (see {@link com.example.blockassist.core.InteractionController}).
 *
 * <p>Milestone scope: mature crops only, break only. No replanting.
 */
public final class CropAutomation {
	private CropAutomation() {
	}

	public static boolean isValidTarget(BlockState state, BlockAssistConfig config) {
		if (!(state.getBlock() instanceof CropBlock)) {
			return false;
		}
		return !config.requireMatureCrop || BlockUtils.isMatureCrop(state);
	}
}
