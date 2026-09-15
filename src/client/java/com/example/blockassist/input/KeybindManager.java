package com.example.blockassist.input;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import org.lwjgl.glfw.GLFW;

import com.example.blockassist.BlockAssist;
import com.example.blockassist.config.BlockAssistConfig;
import com.example.blockassist.config.ConfigManager;

/**
 * Registers BlockAssist's key mappings and dispatches their toggle actions
 * once per client tick. Toggles flip fields directly on the persisted
 * {@link BlockAssistConfig} (single source of truth) and save it.
 */
public final class KeybindManager {
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(BlockAssist.MOD_ID, "main"));

	private static KeyMapping toggleAutomationKey;
	private static KeyMapping toggleCropAutomationKey;
	private static KeyMapping toggleHudKey;

	private KeybindManager() {
	}

	public static void register() {
		toggleAutomationKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.blockassist.toggle_automation", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY));
		toggleCropAutomationKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.blockassist.toggle_crop_automation", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY));
		toggleHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.blockassist.toggle_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(KeybindManager::onClientTick);
	}

	private static void onClientTick(Minecraft client) {
		BlockAssistConfig config = ConfigManager.get();
		boolean changed = false;

		while (toggleAutomationKey.consumeClick()) {
			config.enabled = !config.enabled;
			BlockAssist.LOGGER.info("Automation toggled: {}", config.enabled ? "ON" : "OFF");
			changed = true;
		}

		while (toggleCropAutomationKey.consumeClick()) {
			config.cropAutomationEnabled = !config.cropAutomationEnabled;
			BlockAssist.LOGGER.info("Crop automation toggled: {}", config.cropAutomationEnabled ? "ON" : "OFF");
			changed = true;
		}

		while (toggleHudKey.consumeClick()) {
			config.hudEnabled = !config.hudEnabled;
			changed = true;
		}

		if (changed) {
			ConfigManager.save();
		}
	}
}
