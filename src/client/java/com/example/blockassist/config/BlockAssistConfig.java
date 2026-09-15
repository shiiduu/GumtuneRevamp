package com.example.blockassist.config;

import java.util.LinkedHashSet;
import java.util.Set;

import com.example.blockassist.core.RotationMode;
import com.example.blockassist.core.SelectionMode;

/**
 * Plain data holder, serialized as-is to/from JSON by {@link ConfigManager}.
 * Field names are the on-disk config keys.
 */
public class BlockAssistConfig {
	public boolean enabled = false;
	public boolean cropAutomationEnabled = false;

	public double actionsPerSecond = 10.0;

	public int range = 5;
	public int verticalRange = 3;

	public SelectionMode selectionMode = SelectionMode.CLOSEST;

	public RotationMode rotationMode = RotationMode.SMOOTH;
	public double rotationSpeed = 12.0;

	public boolean lineOfSight = true;
	public boolean requireMatureCrop = true;

	public long attemptMemoryMs = 1000;

	public boolean hudEnabled = true;
	public boolean debugMode = false;

	public Set<String> blockWhitelist = new LinkedHashSet<>();
	public Set<String> blockBlacklist = new LinkedHashSet<>();
}
