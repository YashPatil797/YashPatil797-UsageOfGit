package com.example.shortener.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.shortener.domain.RedirectType;

class UrlValidatorTest {

	private UrlValidator validator;

	@BeforeEach
	void setUp() {
		validator = new UrlValidator();
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://example.com", "https://example.com/path?query=value" })
	void acceptsHttpAndHttpsUrls(String url) {
		assertDoesNotThrow(() -> validator.validate(url));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"javascript:alert(1)",
			"data:text/plain,hello",
			"ftp://example.com/file",
			"https:///missing-host",
			" "
	})
	void rejectsUnsupportedOrMalformedUrls(String url) {
		assertThrows(IllegalArgumentException.class, () -> validator.validate(url));
	}

	@Test
	void rejectsUrlsLongerThan2048Characters() {
		String url = "https://example.com/" + "a".repeat(2049);

		assertThrows(IllegalArgumentException.class, () -> validator.validate(url));
	}

	@ParameterizedTest
	@ValueSource(strings = { "abcd", "my-sale", "Alias_123", "abcdefghijklmnopqrstuvwxyz1234", "apiv2", "actuator1", "healthy" })
	void acceptsValidAliases(String alias) {
		assertDoesNotThrow(() -> validator.validateAlias(alias));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "abc", "alias!", "abcdefghijklmnopqrstuvwxyz12345", "actuator", "health" })
	void rejectsInvalidAliases(String alias) {
		assertThrows(IllegalArgumentException.class, () -> validator.validateAlias(alias));
	}

	@Test
	void parsesRedirectTypes() {
		assertEquals(RedirectType.TEMPORARY, validator.parseRedirectType(null));
		assertEquals(RedirectType.TEMPORARY, validator.parseRedirectType("temporary"));
		assertEquals(RedirectType.PERMANENT, validator.parseRedirectType("permanent"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "PERMANENT", "301", "forever" })
	void rejectsUnknownRedirectTypes(String value) {
		assertThrows(IllegalArgumentException.class, () -> validator.parseRedirectType(value));
	}

	@Test
	void acceptsMissingAlias() {
		assertDoesNotThrow(() -> validator.validateAlias(null));
	}
}
