package com.example.blockassist.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import com.example.blockassist.BlockAssist;
import com.example.blockassist.config.ConfigManager;
import com.example.blockassist.core.AutomationController;
import com.example.blockassist.input.KeybindManager;
import com.example.blockassist.ui.HudRenderer;

public class BlockAssistClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ConfigManager.load();

		AutomationController automationController = new AutomationController();
		ClientTickEvents.END_CLIENT_TICK.register(automationController::onClientTick);

		KeybindManager.register(automationController);
		HudRenderer.register(automationController);

		BlockAssist.LOGGER.info("BlockAssist initialized");
	}
}
