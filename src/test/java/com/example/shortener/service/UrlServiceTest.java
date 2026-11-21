package com.example.shortener.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.shortener.api.dto.CreateUrlRequest;
import com.example.shortener.api.dto.CreateUrlResponse;
import com.example.shortener.api.dto.UrlMetadataResponse;
import com.example.shortener.cache.UrlCache;
import com.example.shortener.cache.UrlCache.CachedUrl;
import com.example.shortener.domain.Base62;
import com.example.shortener.domain.RedirectType;
import com.example.shortener.domain.ShortUrl;
import com.example.shortener.repository.ShortUrlRepository;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

	@Mock
	private ShortUrlRepository repository;

	@Mock
	private UrlCache cache;

	private UrlService service;

	@BeforeEach
	void setUp() {
		service = new UrlService(repository, new UrlValidator(), cache, "http://sho.rt/");
	}

	@Test
	void createUsesSequenceValueAndReturnsOneTimeToken() {
		when(repository.nextId()).thenReturn(238328L);
		ArgumentCaptor<ShortUrl> inserted = ArgumentCaptor.forClass(ShortUrl.class);
		when(repository.insertIfCodeAvailable(inserted.capture())).thenReturn(1);

		CreateUrlResponse response = service.create(request("https://example.com", null, null));

		assertEquals("1000", response.code());
		assertEquals("http://sho.rt/1000", response.shortUrl());
		assertEquals("https://example.com", response.originalUrl());
		assertTrue(response.active());
		assertEquals(0, response.hitCount());
		assertEquals(RedirectType.TEMPORARY, response.redirectType());
		assertEquals(43, response.managementToken().length());
		assertEquals(238328L, inserted.getValue().getId());
		assertEquals(ManagementTokens.hash(response.managementToken()), inserted.getValue().getManagementTokenHash());
		assertNotEquals(response.managementToken(), inserted.getValue().getManagementTokenHash());
	}

	@Test
	void createStoresPermanentRedirectType() {
		when(repository.nextId()).thenReturn(238328L);
		when(repository.insertIfCodeAvailable(any())).thenReturn(1);

		CreateUrlResponse response = service.create(request("https://example.com", null, "permanent"));

		assertEquals(RedirectType.PERMANENT, response.redirectType());
		verify(repository).insertIfCodeAvailable(argThat(url -> url.getRedirectType() == RedirectType.PERMANENT));
	}

	@Test
	void createRejectsUnknownRedirectType() {
		assertThrows(IllegalArgumentException.class,
			() -> service.create(request("https://example.com", null, "forever")));
		verify(repository, never()).insertIfCodeAvailable(any());
	}

	@Test
	void createRetriesWhenGeneratedCodeIsTaken() {
		when(repository.nextId()).thenReturn(238328L, 238329L);
		when(repository.insertIfCodeAvailable(any())).thenReturn(0, 1);

		CreateUrlResponse response = service.create(request("https://example.com", null, null));

		assertEquals("1001", response.code());
	}

	@Test
	void createFailsAfterThreeTakenGeneratedCodes() {
		when(repository.nextId()).thenReturn(238328L, 238329L, 238330L);
		when(repository.insertIfCodeAvailable(any())).thenReturn(0);

		assertThrows(IllegalStateException.class,
			() -> service.create(request("https://example.com", null, null)));
		verify(repository, times(3)).insertIfCodeAvailable(any());
	}

	@Test
	void createSkipsGeneratedCodeThatIsReserved() {
		long reservedId = Base62.decode("actuator");
		when(repository.nextId()).thenReturn(reservedId, reservedId + 1);
		when(repository.insertIfCodeAvailable(any())).thenReturn(1);

		CreateUrlResponse response = service.create(request("https://example.com", null, null));

		assertEquals(Base62.encode(reservedId + 1), response.code());
		verify(repository, never()).insertIfCodeAvailable(argThat(url -> url.getCode().equals("actuator")));
	}

	@Test
	void createThrowsWhenAliasIsTaken() {
		when(repository.nextId()).thenReturn(238328L);
		when(repository.insertIfCodeAvailable(any())).thenReturn(0);

		assertThrows(AliasTakenException.class,
			() -> service.create(request("https://example.com", "my-sale", null)));
	}

	@Test
	void resolveThrowsWhenCodeDoesNotExist() {
		when(cache.get("missing")).thenReturn(Optional.empty());
		when(repository.findByCode("missing")).thenReturn(Optional.empty());

		assertThrows(NotFoundException.class, () -> service.resolve("missing"));
	}

	@Test
	void resolveThrowsWhenLinkHasExpired() {
		ShortUrl expired = new ShortUrl(1L, "expired", "https://example.com", Instant.now().minusSeconds(1));
		when(cache.get("expired")).thenReturn(Optional.empty());
		when(repository.findByCode("expired")).thenReturn(Optional.of(expired));

		assertThrows(ExpiredException.class, () -> service.resolve("expired"));
		verify(repository, never()).incrementClickCount(anyString());
	}

	@Test
	void resolveCountsCacheHitWithoutReadingRow() {
		when(cache.get("code")).thenReturn(Optional.of(new CachedUrl("https://example.com", RedirectType.PERMANENT)));
		when(repository.incrementClickCount("code")).thenReturn(1);

		ResolvedUrl resolved = service.resolve("code");

		assertEquals(new ResolvedUrl("https://example.com", RedirectType.PERMANENT), resolved);
		verify(repository, never()).findByCode(anyString());
	}

	@Test
	void resolveCountsCacheMissOnce() {
		ShortUrl shortUrl = new ShortUrl(1L, "code", "https://example.com", null);
		when(cache.get("code")).thenReturn(Optional.empty());
		when(repository.findByCode("code")).thenReturn(Optional.of(shortUrl));
		when(repository.incrementClickCount("code")).thenReturn(1);

		assertEquals("https://example.com", service.resolve("code").url());
		verify(repository, times(1)).incrementClickCount("code");
		verify(cache).put("code", "https://example.com", RedirectType.TEMPORARY, null);
	}

	@Test
	void resolveDoesNotRedirectStaleCacheEntryForDeletedRow() {
		when(cache.get("code")).thenReturn(Optional.of(new CachedUrl("https://example.com", RedirectType.TEMPORARY)));
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
		verify(cache, never()).put(anyString(), anyString(), any(), any());
	}

	@Test
	void redisFailureFallsBackToRepository() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("Redis unavailable"));
		UrlService serviceWithFailingRedis = new UrlService(
			repository, new UrlValidator(), new UrlCache(redis), "http://sho.rt");
		ShortUrl shortUrl = new ShortUrl(1L, "code", "https://example.com", null);
		when(repository.findByCode("code")).thenReturn(Optional.of(shortUrl));
		when(repository.incrementClickCount("code")).thenReturn(1);

		assertEquals("https://example.com", serviceWithFailingRedis.resolve("code").url());
		verify(repository).findByCode("code");
	}

	@Test
	void metadataDoesNotCountOrExposeToken() {
		ShortUrl shortUrl = owned("code", "secret-token");
		when(repository.findByCode("code")).thenReturn(Optional.of(shortUrl));

		UrlMetadataResponse metadata = service.metadata("code");

		assertEquals("https://example.com", metadata.originalUrl());
		assertTrue(metadata.active());
		verify(repository, never()).incrementClickCount(anyString());
	}

	@Test
	void deactivateWithOwnerTokenDeactivatesAndEvicts() {
		when(repository.findByCode("code")).thenReturn(Optional.of(owned("code", "secret-token")));

		UrlMetadataResponse metadata = service.deactivate("code", "secret-token");

		assertFalse(metadata.active());
		verify(repository).deactivate("code");
		verify(cache).evict("code");
	}

	@Test
	void deactivateWithWrongTokenIsForbidden() {
		when(repository.findByCode("code")).thenReturn(Optional.of(owned("code", "secret-token")));

		assertThrows(ForbiddenException.class, () -> service.deactivate("code", "other-token"));
		verify(repository, never()).deactivate(anyString());
	}

	@Test
	void deactivateLegacyLinkWithoutOwnerIsForbidden() {
		when(repository.findByCode("code")).thenReturn(Optional.of(new ShortUrl(1L, "code", "https://example.com", null)));

		assertThrows(ForbiddenException.class, () -> service.deactivate("code", "any-token"));
	}

	@Test
	void deactivateUnknownCodeIsNotFound() {
		when(repository.findByCode("missing")).thenReturn(Optional.empty());

		assertThrows(NotFoundException.class, () -> service.deactivate("missing", "any-token"));
	}

	private static CreateUrlRequest request(String url, String alias, String redirectType) {
		return new CreateUrlRequest(url, alias, null, redirectType);
	}

	private static ShortUrl owned(String code, String token) {
		return new ShortUrl(1L, code, "https://example.com", Instant.now(), null, RedirectType.TEMPORARY,
			java.util.UUID.randomUUID(), ManagementTokens.hash(token));
	}
}
