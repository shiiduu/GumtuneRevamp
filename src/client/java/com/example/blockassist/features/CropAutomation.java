package com.example.blockassist.features;

import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.example.blockassist.config.BlockAssistConfig;
import com.example.blockassist.util.BlockUtils;

/**
 * Crop-specific target acceptance, kept separate from generic block
 * interaction (see {@link com.example.blockassist.core.InteractionController}).
 *
 * <p>Milestone scope: mature crops only, break only. {@code replantEnabled}/
 * {@code replantDelayMs} exist in config for the GUI but are not acted on
 * yet - no replanting logic has been implemented.
 */
public final class CropAutomation {
	private CropAutomation() {
	}

	public static boolean isValidTarget(BlockState state, BlockAssistConfig config) {
		if (!(state.getBlock() instanceof CropBlock)) {
			return false;
		}
		if (!config.cropWhitelist.isEmpty() && !BlockUtils.isWhitelisted(state, config.cropWhitelist)) {
			return false;
		}
		return !config.requireMatureCrop || BlockUtils.isMatureCrop(state);
	}
}
