package com.example.shortener.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.shortener.domain.RedirectType;

@Component
public class UrlValidator {

	private static final int MAX_URL_LENGTH = 2048;
	private static final Pattern ALIAS_PATTERN = Pattern.compile("[A-Za-z0-9_-]{4,30}");
	// Must match the exclusions in RedirectController's route pattern.
	private static final Set<String> RESERVED_CODES = Set.of("api", "actuator", "health");

	public static boolean isReserved(String code) {
		return RESERVED_CODES.contains(code);
	}

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
		if (alias != null && isReserved(alias)) {
			throw new IllegalArgumentException("Alias is reserved");
		}
	}

	public RedirectType parseRedirectType(String value) {
		if (value == null) {
			return RedirectType.TEMPORARY;
		}
		return switch (value) {
			case "temporary" -> RedirectType.TEMPORARY;
			case "permanent" -> RedirectType.PERMANENT;
			default -> throw new IllegalArgumentException("redirectType must be \"temporary\" or \"permanent\"");
		};
	}

	public void validateExpiresInDays(Integer expiresInDays) {
		if (expiresInDays != null && (expiresInDays < 1 || expiresInDays > 365)) {
			throw new IllegalArgumentException("expiresInDays must be between 1 and 365");
		}
	}
}
