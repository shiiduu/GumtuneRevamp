package com.example.blockassist.ui;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.CommonColors;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import com.example.blockassist.BlockAssist;
import com.example.blockassist.config.BlockAssistConfig;
import com.example.blockassist.config.ConfigManager;
import com.example.blockassist.core.AutomationController;
import com.example.blockassist.core.InteractionStats;
import com.example.blockassist.core.Target;

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
		Target target = controller.state().target();

		if (config.enabled && !controller.hasActiveFeature()) {
			graphics.text(Minecraft.getInstance().font, "BlockAssist: ON, but no feature is enabled!", MARGIN, MARGIN, CommonColors.RED);
			graphics.text(Minecraft.getInstance().font, "Press N (crop automation) or open config (K)", MARGIN, MARGIN + LINE_HEIGHT, CommonColors.RED);
			return;
		}

		String[] lines = {
				"BlockAssist: " + (config.enabled ? "ON" : "OFF"),
				"Target: " + (target != null ? target.state().getBlock().getName().getString() + " @ " + target.pos().toShortString() : "-"),
				"Face: " + (target != null ? target.face() : "-"),
				"State: " + controller.state().phase(),
				"",
				String.format("Acquire: %.1f/s", stats.acquiredPerSecond()),
				String.format("Request: %.1f/s", stats.requestedPerSecond()),
				String.format("Complete: %.1f/s", stats.completedPerSecond()),
				String.format("Confirm: %.1f/s", stats.confirmedPerSecond()),
				String.format("Failed: %.1f/s", stats.failedPerSecond()),
				"",
				"Rotation: " + stats.lastPhaseDurationMs("ROTATING") + " ms",
				"Confirm: " + stats.lastPhaseDurationMs("CONFIRM") + " ms",
				"Scan: " + stats.lastPhaseDurationMs("SCAN") + " ms",
				"Pending: " + controller.pendingConfirmations()
		};

		int y = MARGIN;
		for (String line : lines) {
			if (!line.isEmpty()) {
				graphics.text(Minecraft.getInstance().font, line, MARGIN, y, CommonColors.WHITE);
			}
			y += LINE_HEIGHT;
		}
	}
}
