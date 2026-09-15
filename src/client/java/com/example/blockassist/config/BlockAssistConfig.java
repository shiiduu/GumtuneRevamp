package com.example.blockassist.config;

import java.util.LinkedHashSet;
import java.util.Set;

import com.example.blockassist.core.FaceMode;
import com.example.blockassist.core.RotationMode;
import com.example.blockassist.core.SelectionMode;

/**
 * Plain data holder, serialized as-is to/from JSON by {@link ConfigManager}.
 * Field names are the on-disk config keys.
 */
public class BlockAssistConfig {
	// --- General ---
	public boolean enabled = false;
	public boolean cropAutomationEnabled = false;
	public boolean hudEnabled = true;
	public boolean debugMode = false;

	// --- Targeting ---
	public int range = 5;
	public int verticalRange = 3;
	public SelectionMode selectionMode = SelectionMode.CLOSEST;
	public boolean lineOfSight = true;
	public Set<String> blockWhitelist = new LinkedHashSet<>();
	public Set<String> blockBlacklist = new LinkedHashSet<>();

	// --- Rotation ---
	public RotationMode rotationMode = RotationMode.SMOOTH;
	public double rotationSpeed = 12.0;
	public FaceMode faceMode = FaceMode.ANY;
	public boolean allowNearestVisibleFaceWhenAny = true;

	// --- Interaction ---
	public double actionsPerSecond = 10.0;
	public long interactionStartCadenceMs = 50;
	public long breakTimeoutMs = 5000;
	public long confirmationTimeoutMs = 2000;
	public long retryDelayMs = 100;
	public int maxAttemptsPerTarget = 5;

	// --- Crops ---
	public boolean requireMatureCrop = true;
	public Set<String> cropWhitelist = new LinkedHashSet<>();
	public boolean replantEnabled = false;
	public long replantDelayMs = 250;

	// --- Recent attempts ---
	public long attemptMemoryMs = 1000;
}
