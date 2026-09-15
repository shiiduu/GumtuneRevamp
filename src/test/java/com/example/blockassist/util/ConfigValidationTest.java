package com.example.blockassist.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

class ConfigValidationTest {

	@Test
	void parseIntAcceptsInRangeValue() {
		OptionalInt result = ConfigValidation.parseInt("5", 1, 32);
		assertTrue(result.isPresent());
		assertEquals(5, result.getAsInt());
	}

	@Test
	void parseIntRejectsOutOfRangeValue() {
		assertTrue(ConfigValidation.parseInt("100", 1, 32).isEmpty());
		assertTrue(ConfigValidation.parseInt("0", 1, 32).isEmpty());
	}

	@Test
	void parseIntNeverThrowsOnGarbageInput() {
		assertFalse(ConfigValidation.parseInt("not a number", 1, 32).isPresent());
		assertFalse(ConfigValidation.parseInt("", 1, 32).isPresent());
		assertFalse(ConfigValidation.parseInt("12.5", 1, 32).isPresent());
	}

	@Test
	void parseLongRespectsBounds() {
		OptionalLong ok = ConfigValidation.parseLong("1000", 0, 60000);
		assertTrue(ok.isPresent());
		assertEquals(1000L, ok.getAsLong());
		assertTrue(ConfigValidation.parseLong("-1", 0, 60000).isEmpty());
	}

	@Test
	void parseDoubleRejectsNonFiniteValues() {
		assertTrue(ConfigValidation.parseDouble("NaN", 0.1, 60.0).isEmpty());
		assertTrue(ConfigValidation.parseDouble("Infinity", 0.1, 60.0).isEmpty());
		OptionalDouble ok = ConfigValidation.parseDouble("12.5", 0.1, 60.0);
		assertTrue(ok.isPresent());
		assertEquals(12.5, ok.getAsDouble());
	}
}
