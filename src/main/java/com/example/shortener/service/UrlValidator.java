package com.example.shortener.service;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.stereotype.Component;

@Component
public class UrlValidator {

	private static final int MAX_URL_LENGTH = 2048;

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
}
