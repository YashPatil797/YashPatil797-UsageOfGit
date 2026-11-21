package com.example.shortener.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class UrlValidator {

	private static final int MAX_URL_LENGTH = 2048;
	private static final Pattern ALIAS_PATTERN = Pattern.compile("[A-Za-z0-9_-]{4,30}");

	public void validate(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("URL is required");
		}
		if (value.length() > MAX_URL_LENGTH) {
			throw new IllegalArgumentException("URL must not exceed 2048 characters");
		}

		try {
			URI uri = new URI(value);
			String scheme = uri.getScheme();
			if (scheme == null
					|| (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
					|| uri.getHost() == null) {
				throw new IllegalArgumentException("URL must be an absolute HTTP or HTTPS URL with a host");
			}
		}
		catch (URISyntaxException exception) {
			throw new IllegalArgumentException("URL is not valid", exception);
		}
	}

	public void validateAlias(String alias) {
		if (alias != null && !ALIAS_PATTERN.matcher(alias).matches()) {
			throw new IllegalArgumentException("Alias must be 4 to 30 URL-safe characters");
		}
	}

	public void validateExpiresInDays(Integer expiresInDays) {
		if (expiresInDays != null && (expiresInDays < 1 || expiresInDays > 365)) {
			throw new IllegalArgumentException("expiresInDays must be between 1 and 365");
		}
	}
}
