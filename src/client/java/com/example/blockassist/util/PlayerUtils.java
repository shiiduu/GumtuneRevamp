package com.example.blockassist.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/**
 * Null-safety helpers for reaching client/player/world state. Every access to
 * Minecraft state must go through these (or be on the client thread already
 * via a Fabric client event), never from a background thread.
 */
public final class PlayerUtils {
	private PlayerUtils() {
	}

	public static Minecraft client() {
		return Minecraft.getInstance();
	}

	public static LocalPlayer player() {
		return client().player;
	}

	public static ClientLevel level() {
		return client().level;
	}

	public static boolean isWorldReady() {
		Minecraft client = client();
		return client.player != null && client.level != null;
	}

	public static boolean isPlayerAlive() {
		LocalPlayer player = player();
		return player != null && player.isAlive();
	}

	public static boolean isScreenOpen() {
		return client().screen != null;
	}
}
