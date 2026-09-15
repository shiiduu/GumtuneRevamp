package com.example.blockassist.util;

import com.example.blockassist.BlockAssist;
import com.example.blockassist.config.ConfigManager;

/** Guards debug-only logging behind {@code config.debugMode} so disabled runs stay silent. */
public final class DebugLog {
	private DebugLog() {
	}

	public static void log(String format, Object... args) {
		if (ConfigManager.get().debugMode) {
			BlockAssist.LOGGER.info(format, args);
		}
	}
}
