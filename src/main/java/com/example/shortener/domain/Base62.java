package com.example.shortener.domain;

public final class Base62 {

	private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
	private static final int RADIX = ALPHABET.length();

	private Base62() {
	}

	public static String encode(long number) {
		if (number < 0) {
			throw new IllegalArgumentException("number must be non-negative");
		}
		if (number == 0) {
			return "0";
		}

		StringBuilder encoded = new StringBuilder();
		while (number > 0) {
			encoded.append(ALPHABET.charAt((int) (number % RADIX)));
			number /= RADIX;
		}
		return encoded.reverse().toString();
	}

	public static long decode(String value) {
		if (value == null || value.isEmpty()) {
			throw new IllegalArgumentException("value must not be null or empty");
		}

		long decoded = 0;
		for (int index = 0; index < value.length(); index++) {
			int digit = ALPHABET.indexOf(value.charAt(index));
			if (digit < 0) {
				throw new IllegalArgumentException("value contains an invalid Base62 character");
			}
			if (decoded > (Long.MAX_VALUE - digit) / RADIX) {
				throw new IllegalArgumentException("value exceeds the range of a long");
			}
			decoded = decoded * RADIX + digit;
		}
		return decoded;
	}
}
