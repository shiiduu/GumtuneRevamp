package com.example.blockassist.util;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Parsing/range-clamping for numeric config fields, shared by
 * {@code ConfigScreen}'s text fields. Never throws on malformed input -
 * callers get an empty Optional and should simply leave the config
 * unchanged rather than crash.
 */
public final class ConfigValidation {
	private ConfigValidation() {
	}

	public static OptionalInt parseInt(String text, int min, int max) {
		try {
			int value = Integer.parseInt(text.trim());
			return value >= min && value <= max ? OptionalInt.of(value) : OptionalInt.empty();
		} catch (NumberFormatException e) {
			return OptionalInt.empty();
		}
	}

	public static OptionalLong parseLong(String text, long min, long max) {
		try {
			long value = Long.parseLong(text.trim());
			return value >= min && value <= max ? OptionalLong.of(value) : OptionalLong.empty();
		} catch (NumberFormatException e) {
			return OptionalLong.empty();
		}
	}

	public static OptionalDouble parseDouble(String text, double min, double max) {
		try {
			double value = Double.parseDouble(text.trim());
			return value >= min && value <= max && Double.isFinite(value) ? OptionalDouble.of(value) : OptionalDouble.empty();
		} catch (NumberFormatException e) {
			return OptionalDouble.empty();
		}
	}
}
