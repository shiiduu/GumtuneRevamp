package com.example.blockassist.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import com.example.blockassist.BlockAssist;

/** Loads/saves {@link BlockAssistConfig} as human-readable JSON under the standard Fabric config directory. */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("blockassist.json");

	private static BlockAssistConfig config = new BlockAssistConfig();

	private ConfigManager() {
	}

	public static BlockAssistConfig get() {
		return config;
	}

	public static void load() {
		if (!Files.exists(CONFIG_PATH)) {
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
			BlockAssistConfig loaded = GSON.fromJson(reader, BlockAssistConfig.class);
			config = loaded != null ? loaded : new BlockAssistConfig();
		} catch (IOException | RuntimeException e) {
			BlockAssist.LOGGER.warn("Failed to load blockassist.json, using defaults", e);
			config = new BlockAssistConfig();
		}
	}

	public static void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			BlockAssist.LOGGER.warn("Failed to save blockassist.json", e);
		}
	}
}
