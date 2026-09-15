package com.example.blockassist.input;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import org.lwjgl.glfw.GLFW;

import com.example.blockassist.BlockAssist;

/**
 * Registers BlockAssist's key mappings and dispatches their toggle actions once per client tick.
 * Milestone 1-3 scope only: a single toggle for the automation master switch.
 */
public final class KeybindManager {
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(BlockAssist.MOD_ID, "main"));

	private static KeyMapping toggleAutomationKey;

	private static boolean automationEnabled = false;

	private KeybindManager() {
	}

	public static void register() {
		toggleAutomationKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.blockassist.toggle_automation",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_B,
				CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(KeybindManager::onClientTick);
	}

	private static void onClientTick(net.minecraft.client.Minecraft client) {
		while (toggleAutomationKey.consumeClick()) {
			automationEnabled = !automationEnabled;
			BlockAssist.LOGGER.info("Automation toggled: {}", automationEnabled ? "ON" : "OFF");
		}
	}

	public static boolean isAutomationEnabled() {
		return automationEnabled;
	}
}
