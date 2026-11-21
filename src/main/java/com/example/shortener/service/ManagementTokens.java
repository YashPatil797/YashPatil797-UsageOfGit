package com.example.shortener.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

final class ManagementTokens {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int TOKEN_BYTES = 32;

	private ManagementTokens() {
	}

	static String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	// Tokens carry 256 random bits, so a fast unsalted digest is sufficient; no password-style KDF is needed.
	static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	static boolean matches(String token, String storedHash) {
		if (token == null || storedHash == null) {
			return false;
		}
		return MessageDigest.isEqual(
			hash(token).getBytes(StandardCharsets.US_ASCII),
			storedHash.getBytes(StandardCharsets.US_ASCII));
	}
}
