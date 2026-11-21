package com.example.shortener.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class Base62Test {

	@Test
	void roundTripsSupportedValues() {
		long[] values = { 0, 1, 61, 62, 238328, Long.MAX_VALUE };

		for (long value : values) {
			assertEquals(value, Base62.decode(Base62.encode(value)));
		}
	}

	@Test
	void encodesAlphabetBoundaries() {
		assertEquals("Z", Base62.encode(61));
		assertEquals("10", Base62.encode(62));
	}

	@Test
	void rejectsInvalidCharacter() {
		assertThrows(IllegalArgumentException.class, () -> Base62.decode("-"));
	}
}
