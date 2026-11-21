package com.example.shortener.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.example.shortener.domain.RedirectType;

@Component
public class UrlCache {

	private static final Logger LOGGER = LoggerFactory.getLogger(UrlCache.class);
	private static final Duration MAX_TTL = Duration.ofHours(24);
	// Versioned so instances on the previous value format never read entries written in this one.
	private static final String KEY_PREFIX = "url:v2:";

	private final StringRedisTemplate redis;

	public UrlCache(StringRedisTemplate redis) {
		this.redis = redis;
	}

	public Optional<CachedUrl> get(String code) {
		try {
			String value = redis.opsForValue().get(KEY_PREFIX + code);
			if (value == null) {
				return Optional.empty();
			}

			int typeSeparator = value.lastIndexOf('|');
			int expirySeparator = typeSeparator < 0 ? -1 : value.lastIndexOf('|', typeSeparator - 1);
			if (expirySeparator < 0) {
				throw new IllegalArgumentException("Cached URL has an invalid format");
			}

			long expiresAtMillis = Long.parseLong(value.substring(expirySeparator + 1, typeSeparator));
			if (expiresAtMillis != 0 && expiresAtMillis <= System.currentTimeMillis()) {
				return Optional.empty();
			}
			RedirectType redirectType = RedirectType.valueOf(value.substring(typeSeparator + 1));
			return Optional.of(new CachedUrl(value.substring(0, expirySeparator), redirectType));
		}
		catch (Exception exception) {
			LOGGER.warn("Redis cache read failed for code {}; falling back to the database", code, exception);
			return Optional.empty();
		}
	}

	public void put(String code, String longUrl, RedirectType redirectType, Instant expiresAt) {
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

		String value = longUrl + "|" + expiresAtMillis + "|" + redirectType.name();
		try {
			redis.opsForValue().set(KEY_PREFIX + code, value, ttl);
		}
		catch (Exception exception) {
			LOGGER.warn("Redis cache write failed for code {}; continuing without cache", code, exception);
		}
	}

	public void evict(String code) {
		try {
			redis.delete(KEY_PREFIX + code);
		}
		catch (Exception exception) {
			LOGGER.warn("Redis cache eviction failed for code {}; the database still rejects the stale entry", code,
				exception);
		}
	}

	private static Duration min(Duration first, Duration second) {
		return first.compareTo(second) <= 0 ? first : second;
	}

	public record CachedUrl(String longUrl, RedirectType redirectType) {
	}
}
