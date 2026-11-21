package com.example.shortener.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.shortener.api.dto.CreateUrlRequest;
import com.example.shortener.api.dto.CreateUrlResponse;
import com.example.shortener.cache.UrlCache;
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
		when(repository.findByCode("1000")).thenReturn(Optional.empty());
		when(repository.save(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

		CreateUrlResponse response = service.create(
			new CreateUrlRequest("https://example.com", null, null));

		assertEquals("1000", response.code());
		assertEquals("http://sho.rt/1000", response.shortUrl());

		ArgumentCaptor<ShortUrl> captor = ArgumentCaptor.forClass(ShortUrl.class);
		verify(repository).save(captor.capture());
		assertEquals(238328L, captor.getValue().getId());
		assertEquals("1000", captor.getValue().getCode());
		assertEquals("https://example.com", captor.getValue().getLongUrl());
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

		String resolved = serviceWithFailingRedis.resolve("code");

		assertEquals("https://example.com", resolved);
		verify(repository).findByCode("code");
	}
}
