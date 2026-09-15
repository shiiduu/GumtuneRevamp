package com.example.blockassist.ui;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.CommonColors;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import com.example.blockassist.BlockAssist;
import com.example.blockassist.config.BlockAssistConfig;
import com.example.blockassist.config.ConfigManager;
import com.example.blockassist.core.AutomationController;
import com.example.blockassist.core.InteractionStats;

/** Minimal always-on-top status readout. No interactive elements. */
public final class HudRenderer {
	private static final int LINE_HEIGHT = 10;
	private static final int MARGIN = 6;

	private HudRenderer() {
	}

	public static void register(AutomationController controller) {
		HudElementRegistry.addLast(BlockAssist.id("hud"), (graphics, deltaTracker) -> render(graphics, deltaTracker, controller));
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, AutomationController controller) {
		BlockAssistConfig config = ConfigManager.get();
		if (!config.hudEnabled) {
			return;
		}

		InteractionStats stats = controller.stats();
		BlockPos target = controller.state().targetPos();

		String[] lines = {
				"BlockAssist: " + (config.enabled ? "ON" : "OFF"),
				"Target: " + (target != null ? target.toShortString() : "-"),
				"State: " + controller.state().phase(),
				String.format("Requested: %.1f/s", stats.requestedPerSecond()),
				String.format("Confirmed: %.1f/s", stats.confirmedPerSecond()),
				"Attempts: " + stats.requestedTotal(),
				"Confirmed: " + stats.confirmedTotal(),
				"Failed: " + stats.failedTotal()
		};

		int y = MARGIN;
		for (String line : lines) {
			graphics.text(Minecraft.getInstance().font, line, MARGIN, y, CommonColors.WHITE);
			y += LINE_HEIGHT;
		}
	}
}
