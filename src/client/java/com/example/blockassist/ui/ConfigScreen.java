package com.example.blockassist.ui;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import com.example.blockassist.config.BlockAssistConfig;
import com.example.blockassist.config.ConfigManager;
import com.example.blockassist.core.AutomationController;
import com.example.blockassist.core.FaceMode;
import com.example.blockassist.core.RotationMode;
import com.example.blockassist.core.SelectionMode;
import com.example.blockassist.util.ConfigValidation;

/**
 * Native settings screen - no Cloth Config/Mod Menu dependency (see
 * docs/architecture.md for why: their 26.1.2 API surface hasn't been
 * source-verified the way every other API in this project has, so a plain
 * {@link Screen} built from real, decompiled-and-confirmed vanilla widgets
 * was used instead). All fields write straight through to the live
 * {@link BlockAssistConfig} instance and are saved on close.
 */
public class ConfigScreen extends Screen {
	private static final int LABEL_WIDTH = 150;
	private static final int CONTROL_WIDTH = 150;
	private static final int ROW_HEIGHT = 20;
	private static final int CONTENT_WIDTH = LABEL_WIDTH + CONTROL_WIDTH + 8;

	private final Screen parent;
	private final AutomationController controller;
	private final BlockAssistConfig config = ConfigManager.get();
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

	public ConfigScreen(Screen parent, AutomationController controller) {
		super(Component.literal("BlockAssist Configuration"));
		this.parent = parent;
		this.controller = controller;
	}

	@Override
	protected void init() {
		this.layout.addTitleHeader(this.title, this.font);

		LinearLayout content = LinearLayout.vertical().spacing(4);

		addSectionHeader(content, "General");
		addToggle(content, "Automation enabled", "Master switch for all automation.", () -> config.enabled, v -> config.enabled = v);
		addToggle(content, "Crop automation", "Break mature crops in range.", () -> config.cropAutomationEnabled, v -> config.cropAutomationEnabled = v);
		addToggle(content, "HUD enabled", "Show the status overlay.", () -> config.hudEnabled, v -> config.hudEnabled = v);
		addToggle(content, "Debug mode", "Log target/rotation/interaction/state details.", () -> config.debugMode, v -> config.debugMode = v);

		addSectionHeader(content, "Targeting");
		addIntField(content, "Horizontal range", "Scan radius in blocks (X/Z).", 1, 32, () -> config.range, v -> config.range = v);
		addIntField(content, "Vertical range", "Scan radius in blocks (Y).", 1, 32, () -> config.verticalRange, v -> config.verticalRange = v);
		addEnumCycle(content, "Selection mode", "CLOSEST: nearest block. EASIEST: least rotation needed.",
				SelectionMode.values(), () -> config.selectionMode, v -> config.selectionMode = v);
		addToggle(content, "Require line of sight", "Reject candidates a raycast can't actually reach.", () -> config.lineOfSight, v -> config.lineOfSight = v);
		addSetField(content, "Block whitelist", "Comma-separated block IDs (e.g. minecraft:wheat). Empty = allow all.", () -> config.blockWhitelist, v -> config.blockWhitelist = v);
		addSetField(content, "Block blacklist", "Comma-separated block IDs to always reject.", () -> config.blockBlacklist, v -> config.blockBlacklist = v);

		addSectionHeader(content, "Rotation");
		addEnumCycle(content, "Rotation mode", "INSTANT snaps immediately. SMOOTH turns at the configured speed.",
				RotationMode.values(), () -> config.rotationMode, v -> config.rotationMode = v);
		addDoubleField(content, "Smooth speed", "Degrees per tick when rotation mode is SMOOTH.", 0.5, 180.0, () -> config.rotationSpeed, v -> config.rotationSpeed = v);
		addEnumCycle(content, "Preferred face", "Which face of the block to aim at.",
				FaceMode.values(), () -> config.faceMode, v -> config.faceMode = v);
		addToggle(content, "Nearest visible face (ANY)", "When face mode is ANY, pick whichever usable face needs the least rotation.",
				() -> config.allowNearestVisibleFaceWhenAny, v -> config.allowNearestVisibleFaceWhenAny = v);

		addSectionHeader(content, "Interaction");
		addDoubleField(content, "Actions/sec", "Target-acquisition decision rate. Tick-bound in practice - see docs/26.1.2-api-notes.md.", 0.1, 60.0, () -> config.actionsPerSecond, v -> config.actionsPerSecond = v);
		addLongField(content, "Start cadence (ms)", "Minimum delay between retrying a start request on the same target.", 0, 2000, () -> config.interactionStartCadenceMs, v -> config.interactionStartCadenceMs = v);
		addLongField(content, "Break timeout (ms)", "Abort a stuck BREAKING phase after this long.", 100, 30000, () -> config.breakTimeoutMs, v -> config.breakTimeoutMs = v);
		addLongField(content, "Confirmation timeout (ms)", "How long to wait for a break to be confirmed before counting it.", 50, 10000, () -> config.confirmationTimeoutMs, v -> config.confirmationTimeoutMs = v);
		addLongField(content, "Retry delay (ms)", "Delay between start-request retries.", 0, 2000, () -> config.retryDelayMs, v -> config.retryDelayMs = v);
		addIntField(content, "Max attempts/target", "Give up on a target after this many failed start attempts.", 1, 50, () -> config.maxAttemptsPerTarget, v -> config.maxAttemptsPerTarget = v);
		addLongField(content, "Attempt memory (ms)", "How long a position stays 'recently attempted' and is skipped by the scanner.", 0, 60000, () -> config.attemptMemoryMs, v -> config.attemptMemoryMs = v);

		addSectionHeader(content, "Crops");
		addToggle(content, "Mature only", "Only target fully-grown crops.", () -> config.requireMatureCrop, v -> config.requireMatureCrop = v);
		addSetField(content, "Crop whitelist", "Comma-separated crop block IDs. Empty = all crops.", () -> config.cropWhitelist, v -> config.cropWhitelist = v);
		addToggle(content, "Replant (not yet implemented)", "Reserved for a future milestone - has no effect yet.", () -> config.replantEnabled, v -> config.replantEnabled = v);
		addLongField(content, "Replant delay (ms)", "Reserved for a future milestone - has no effect yet.", 0, 10000, () -> config.replantDelayMs, v -> config.replantDelayMs = v);

		addSectionHeader(content, "Statistics");
		LinearLayout statsRow = LinearLayout.horizontal().spacing(8);
		statsRow.addChild(Button.builder(Component.literal("Reset Statistics"), b -> controller.stats().reset()).width(CONTENT_WIDTH).build());
		content.addChild(statsRow, s -> s.paddingBottom(4));

		ScrollableLayout scrollArea = new ScrollableLayout(this.minecraft, content, 300);
		scrollArea.setMinWidth(CONTENT_WIDTH + 20);
		this.layout.addToContents(scrollArea);

		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> this.onDone()).build());

		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
	}

	@Override
	public void onClose() {
		onDone();
	}

	private void onDone() {
		ConfigManager.save();
		this.minecraft.setScreen(this.parent);
	}

	// --- row helpers ---

	private void addSectionHeader(LinearLayout content, String text) {
		content.addChild(new StringWidget(CONTENT_WIDTH, ROW_HEIGHT, Component.literal("-- " + text + " --"), this.font), s -> s.paddingTop(8).paddingBottom(2));
	}

	private LinearLayout newRow(LinearLayout content, String label, String tooltip) {
		LinearLayout row = LinearLayout.horizontal().spacing(8);
		StringWidget labelWidget = new StringWidget(LABEL_WIDTH, ROW_HEIGHT, Component.literal(label), this.font);
		if (tooltip != null) {
			labelWidget.setTooltip(Tooltip.create(Component.literal(tooltip)));
		}
		row.addChild(labelWidget);
		content.addChild(row, s -> s.paddingBottom(2));
		return row;
	}

	private void addToggle(LinearLayout content, String label, String tooltip, Supplier<Boolean> getter, Consumer<Boolean> setter) {
		LinearLayout row = newRow(content, label, tooltip);
		CycleButton<Boolean> button = CycleButton.onOffBuilder(getter.get())
				.create(0, 0, CONTROL_WIDTH, ROW_HEIGHT, Component.empty(), (b, v) -> setter.accept(v));
		if (tooltip != null) {
			button.setTooltip(Tooltip.create(Component.literal(tooltip)));
		}
		row.addChild(button);
	}

	private <T> void addEnumCycle(LinearLayout content, String label, String tooltip, T[] values, Supplier<T> getter, Consumer<T> setter) {
		LinearLayout row = newRow(content, label, tooltip);
		Function<T, Component> stringifier = value -> Component.literal(value.toString());
		CycleButton<T> button = CycleButton.builder(stringifier, getter.get())
				.withValues(values)
				.create(0, 0, CONTROL_WIDTH, ROW_HEIGHT, Component.empty(), (b, v) -> setter.accept(v));
		if (tooltip != null) {
			button.setTooltip(Tooltip.create(Component.literal(tooltip)));
		}
		row.addChild(button);
	}

	private void addIntField(LinearLayout content, String label, String tooltip, int min, int max, Supplier<Integer> getter, Consumer<Integer> setter) {
		// Never crashes on bad input: an unparseable/out-of-range value just leaves the config untouched.
		addNumberField(content, label, tooltip, String.valueOf(getter.get()),
				text -> ConfigValidation.parseInt(text, min, max).ifPresent(setter::accept));
	}

	private void addLongField(LinearLayout content, String label, String tooltip, long min, long max, Supplier<Long> getter, Consumer<Long> setter) {
		addNumberField(content, label, tooltip, String.valueOf(getter.get()),
				text -> ConfigValidation.parseLong(text, min, max).ifPresent(setter::accept));
	}

	private void addDoubleField(LinearLayout content, String label, String tooltip, double min, double max, Supplier<Double> getter, Consumer<Double> setter) {
		addNumberField(content, label, tooltip, String.valueOf(getter.get()),
				text -> ConfigValidation.parseDouble(text, min, max).ifPresent(setter::accept));
	}

	private void addNumberField(LinearLayout content, String label, String tooltip, String initialValue, Consumer<String> onChange) {
		LinearLayout row = newRow(content, label, tooltip);
		EditBox editBox = new EditBox(this.font, CONTROL_WIDTH, ROW_HEIGHT, Component.literal(label));
		editBox.setValue(initialValue);
		editBox.setMaxLength(20);
		editBox.setResponder(onChange);
		if (tooltip != null) {
			editBox.setTooltip(Tooltip.create(Component.literal(tooltip)));
		}
		row.addChild(editBox);
	}

	private void addSetField(LinearLayout content, String label, String tooltip, Supplier<Set<String>> getter, Consumer<Set<String>> setter) {
		LinearLayout row = newRow(content, label, tooltip);
		EditBox editBox = new EditBox(this.font, CONTROL_WIDTH, ROW_HEIGHT, Component.literal(label));
		editBox.setValue(String.join(", ", getter.get()));
		editBox.setMaxLength(1024);
		editBox.setResponder(text -> {
			Set<String> parsed = new LinkedHashSet<>();
			for (String part : text.split(",")) {
				String trimmed = part.trim();
				if (!trimmed.isEmpty()) {
					parsed.add(trimmed);
				}
			}
			setter.accept(parsed);
		});
		if (tooltip != null) {
			editBox.setTooltip(Tooltip.create(Component.literal(tooltip)));
		}
		row.addChild(editBox);
	}
}
