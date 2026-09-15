package com.example.blockassist.client;

import net.fabricmc.api.ClientModInitializer;

import com.example.blockassist.BlockAssist;
import com.example.blockassist.input.KeybindManager;

public class BlockAssistClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		KeybindManager.register();

		BlockAssist.LOGGER.info("BlockAssist initialized");
	}
}
