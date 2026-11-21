package com.example.shortener.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ManagementTokensTest {

	@Test
	void generatesDistinctUrlSafeTokens() {
		String first = ManagementTokens.generate();
		String second = ManagementTokens.generate();

		assertNotEquals(first, second);
		assertEquals(43, first.length());
		assertTrue(first.matches("[A-Za-z0-9_-]+"));
	}

	@Test
	void matchesOnlyTheOriginalToken() {
		String token = ManagementTokens.generate();
		String hash = ManagementTokens.hash(token);

		assertEquals(64, hash.length());
		assertTrue(ManagementTokens.matches(token, hash));
		assertFalse(ManagementTokens.matches(token + "x", hash));
		assertFalse(ManagementTokens.matches(null, hash));
		assertFalse(ManagementTokens.matches(token, null));
	}
}
