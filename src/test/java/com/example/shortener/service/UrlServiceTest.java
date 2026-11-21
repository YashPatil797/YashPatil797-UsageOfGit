package com.example.shortener.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.shortener.api.dto.CreateUrlRequest;
import com.example.shortener.api.dto.CreateUrlResponse;
import com.example.shortener.cache.UrlCache;
import com.example.shortener.domain.Base62;
import com.example.shortener.domain.ShortUrl;
import com.example.shortener.repository.ShortUrlRepository;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

	@Mock
	private ShortUrlRepository repository;

	@Mock
	private UrlValidator validator;

	@Mock
	private UrlCache cache;

	private UrlService service;

	@BeforeEach
	void setUp() {
		service = new UrlService(repository, validator, cache, "http://sho.rt/");
	}

	@Test
	void createUsesSequenceValueAndBase62Code() {
		when(repository.nextId()).thenReturn(238328L);
		when(repository.insertIfCodeAvailable(238328L, "1000", "https://example.com", null)).thenReturn(1);

		CreateUrlResponse response = service.create(
			new CreateUrlRequest("https://example.com", null, null));

		assertEquals("1000", response.code());
		assertEquals("http://sho.rt/1000", response.shortUrl());
	}

	@Test
	void createRetriesWhenGeneratedCodeIsTaken() {
		when(repository.nextId()).thenReturn(238328L, 238329L);
		when(repository.insertIfCodeAvailable(238328L, "1000", "https://example.com", null)).thenReturn(0);
		when(repository.insertIfCodeAvailable(238329L, "1001", "https://example.com", null)).thenReturn(1);

		CreateUrlResponse response = service.create(
			new CreateUrlRequest("https://example.com", null, null));

		assertEquals("1001", response.code());
	}

	@Test
	void createFailsAfterThreeTakenGeneratedCodes() {
		when(repository.nextId()).thenReturn(238328L, 238329L, 238330L);
		when(repository.insertIfCodeAvailable(anyLong(), anyString(), anyString(), isNull())).thenReturn(0);

		assertThrows(IllegalStateException.class, () -> service.create(
			new CreateUrlRequest("https://example.com", null, null)));
		verify(repository, times(3)).insertIfCodeAvailable(anyLong(), anyString(), anyString(), isNull());
	}

	@Test
	void createSkipsGeneratedCodeThatIsReserved() {
		long reservedId = Base62.decode("actuator");
		when(repository.nextId()).thenReturn(reservedId, reservedId + 1);
		String nextCode = Base62.encode(reservedId + 1);
		when(repository.insertIfCodeAvailable(reservedId + 1, nextCode, "https://example.com", null)).thenReturn(1);

		CreateUrlResponse response = service.create(
			new CreateUrlRequest("https://example.com", null, null));

		assertEquals(nextCode, response.code());
		verify(repository, never()).insertIfCodeAvailable(anyLong(), eq("actuator"), anyString(), any());
	}

	@Test
	void createThrowsWhenAliasIsTaken() {
		when(repository.nextId()).thenReturn(238328L);
		when(repository.insertIfCodeAvailable(238328L, "my-sale", "https://example.com", null)).thenReturn(0);

		assertThrows(AliasTakenException.class, () -> service.create(
			new CreateUrlRequest("https://example.com", "my-sale", null)));
	}

	@Test
	void resolveThrowsWhenCodeDoesNotExist() {
		when(cache.get("missing")).thenReturn(Optional.empty());
		when(repository.findByCode("missing")).thenReturn(Optional.empty());

		assertThrows(NotFoundException.class, () -> service.resolve("missing"));
	}

	@Test
	void resolveThrowsWhenLinkHasExpired() {
		ShortUrl expired = new ShortUrl(
			1L,
			"expired",
			"https://example.com",
			Instant.now().minusSeconds(1));
		when(cache.get("expired")).thenReturn(Optional.empty());
		when(repository.findByCode("expired")).thenReturn(Optional.of(expired));

		assertThrows(ExpiredException.class, () -> service.resolve("expired"));
		verify(repository, never()).incrementClickCount(anyString());
	}

	@Test
	void resolveCountsCacheHitWithoutReadingRow() {
		when(cache.get("code")).thenReturn(Optional.of("https://example.com"));
		when(repository.incrementClickCount("code")).thenReturn(1);

		assertEquals("https://example.com", service.resolve("code"));
		verify(repository, never()).findByCode(anyString());
	}

	@Test
	void resolveCountsCacheMissOnce() {
		ShortUrl shortUrl = new ShortUrl(1L, "code", "https://example.com", null);
		when(cache.get("code")).thenReturn(Optional.empty());
		when(repository.findByCode("code")).thenReturn(Optional.of(shortUrl));
		when(repository.incrementClickCount("code")).thenReturn(1);

		assertEquals("https://example.com", service.resolve("code"));
		verify(repository, times(1)).incrementClickCount("code");
		verify(cache).put("code", "https://example.com", null);
	}

	@Test
	void resolveDoesNotRedirectStaleCacheEntryForDeletedRow() {
		when(cache.get("code")).thenReturn(Optional.of("https://example.com"));
		when(repository.incrementClickCount("code")).thenReturn(0);
		when(repository.findByCode("code")).thenReturn(Optional.empty());

		assertThrows(NotFoundException.class, () -> service.resolve("code"));
		verify(repository, times(1)).incrementClickCount("code");
	}

	@Test
	void resolveThrowsExpiredWhenLinkExpiresBeforeIncrement() {
		ShortUrl shortUrl = new ShortUrl(1L, "code", "https://example.com", Instant.now().plusSeconds(60));
		when(cache.get("code")).thenReturn(Optional.empty());
		when(repository.findByCode("code")).thenReturn(Optional.of(shortUrl));
		when(repository.incrementClickCount("code")).thenReturn(0);

		assertThrows(ExpiredException.class, () -> service.resolve("code"));
		verify(cache, never()).put(anyString(), anyString(), any());
	}

	@Test
	void redisFailureFallsBackToRepository() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("Redis unavailable"));
		UrlService serviceWithFailingRedis = new UrlService(
			repository,
			validator,
			new UrlCache(redis),
			"http://sho.rt");
		ShortUrl shortUrl = new ShortUrl(1L, "code", "https://example.com", null);
		when(repository.findByCode("code")).thenReturn(Optional.of(shortUrl));
		when(repository.incrementClickCount("code")).thenReturn(1);

		String resolved = serviceWithFailingRedis.resolve("code");

		assertEquals("https://example.com", resolved);
		verify(repository).findByCode("code");
	}
}
