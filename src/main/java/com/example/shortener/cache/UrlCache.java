package com.example.shortener.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class UrlCache {

	private static final Logger LOGGER = LoggerFactory.getLogger(UrlCache.class);
	private static final Duration MAX_TTL = Duration.ofHours(24);
	private static final String KEY_PREFIX = "url:";

	private final StringRedisTemplate redis;

	public UrlCache(StringRedisTemplate redis) {
		this.redis = redis;
	}

	public Optional<String> get(String code) {
		try {
			String value = redis.opsForValue().get(KEY_PREFIX + code);
			if (value == null) {
				return Optional.empty();
			}

			int separator = value.lastIndexOf('|');
			if (separator < 0) {
				throw new IllegalArgumentException("Cached URL has an invalid format");
			}

			long expiresAtMillis = Long.parseLong(value.substring(separator + 1));
			if (expiresAtMillis != 0 && expiresAtMillis <= System.currentTimeMillis()) {
				return Optional.empty();
			}
			return Optional.of(value.substring(0, separator));
		}
		catch (Exception exception) {
			LOGGER.warn("Redis cache read failed for code {}; falling back to the database", code, exception);
			return Optional.empty();
		}
	}

	public void put(String code, String longUrl, Instant expiresAt) {
		Instant now = Instant.now();
		Duration ttl = MAX_TTL;
		long expiresAtMillis = 0;

		if (expiresAt != null) {
			if (!expiresAt.isAfter(now)) {
				return;
			}
			ttl = min(MAX_TTL, Duration.between(now, expiresAt));
			expiresAtMillis = expiresAt.toEpochMilli();
		}

		String value = longUrl + "|" + expiresAtMillis;
		try {
			redis.opsForValue().set(KEY_PREFIX + code, value, ttl);
		}
		catch (Exception exception) {
			LOGGER.warn("Redis cache write failed for code {}; continuing without cache", code, exception);
		}
	}

	private static Duration min(Duration first, Duration second) {
		return first.compareTo(second) <= 0 ? first : second;
	}
}
