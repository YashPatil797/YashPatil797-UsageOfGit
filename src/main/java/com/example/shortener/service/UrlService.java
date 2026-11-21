package com.example.shortener.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shortener.api.dto.CreateUrlRequest;
import com.example.shortener.api.dto.CreateUrlResponse;
import com.example.shortener.api.dto.UrlMetadataResponse;
import com.example.shortener.cache.UrlCache;
import com.example.shortener.cache.UrlCache.CachedUrl;
import com.example.shortener.domain.Base62;
import com.example.shortener.domain.RedirectType;
import com.example.shortener.domain.ShortUrl;
import com.example.shortener.repository.ShortUrlRepository;

@Service
public class UrlService {

	private static final Logger LOGGER = LoggerFactory.getLogger(UrlService.class);
	private static final int MAX_GENERATION_ATTEMPTS = 3;

	private final ShortUrlRepository repository;
	private final UrlValidator validator;
	private final UrlCache cache;
	private final String baseUrl;

	public UrlService(
			ShortUrlRepository repository,
			UrlValidator validator,
			UrlCache cache,
			@Value("${app.base-url}") String baseUrl) {
		this.repository = repository;
		this.validator = validator;
		this.cache = cache;
		this.baseUrl = stripTrailingSlashes(baseUrl);
	}

	@Transactional
	public CreateUrlResponse create(CreateUrlRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Request body is required");
		}
		validator.validate(request.url());
		validator.validateAlias(request.alias());
		validator.validateExpiresInDays(request.expiresInDays());
		RedirectType redirectType = validator.parseRedirectType(request.redirectType());

		// PostgreSQL stores microseconds, so truncate to keep the create response equal to later metadata.
		Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		Instant expiresAt = request.expiresInDays() == null
				? null
				: createdAt.plus(request.expiresInDays(), ChronoUnit.DAYS);
		String token = ManagementTokens.generate();
		LinkDraft draft = new LinkDraft(
			request.url(), createdAt, expiresAt, redirectType, UUID.randomUUID(), ManagementTokens.hash(token));

		String code = request.alias() == null ? createGenerated(draft) : createAlias(draft, request.alias());

		return new CreateUrlResponse(
			code,
			shortUrl(code),
			request.url(),
			createdAt,
			expiresAt,
			0,
			true,
			redirectType,
			token);
	}

	@Transactional
	public ResolvedUrl resolve(String code) {
		Optional<CachedUrl> cached = cache.get(code);
		// UrlCache rejects entries whose embedded expiry is in the past. A cached entry whose row no longer
		// counts (deleted, expired, or deactivated) falls through to the database so the caller gets 404 or 410.
		if (cached.isPresent() && repository.incrementClickCount(code) == 1) {
			LOGGER.debug("cache hit for code {}", code);
			return new ResolvedUrl(cached.get().longUrl(), cached.get().redirectType());
		}
		LOGGER.debug("cache miss for code {}", code);

		Instant now = Instant.now();
		ShortUrl shortUrl = repository.findByCode(code)
			.orElseThrow(() -> new NotFoundException("Short URL not found"));
		if (!shortUrl.isActive()) {
			throw new InactiveException(code);
		}
		if (shortUrl.getExpiresAt() != null && !shortUrl.getExpiresAt().isAfter(now)) {
			throw new ExpiredException(code);
		}
		if (repository.incrementClickCount(code) == 0) {
			throw new ExpiredException(code);
		}

		cache.put(code, shortUrl.getLongUrl(), shortUrl.getRedirectType(), shortUrl.getExpiresAt());
		return new ResolvedUrl(shortUrl.getLongUrl(), shortUrl.getRedirectType());
	}

	@Transactional(readOnly = true)
	public UrlMetadataResponse metadata(String code) {
		ShortUrl shortUrl = repository.findByCode(code)
			.orElseThrow(() -> new NotFoundException("Short URL not found"));
		return toMetadata(shortUrl, shortUrl.isActive());
	}

	@Transactional
	public UrlMetadataResponse deactivate(String code, String managementToken) {
		ShortUrl shortUrl = repository.findByCode(code)
			.orElseThrow(() -> new NotFoundException("Short URL not found"));
		if (!ManagementTokens.matches(managementToken, shortUrl.getManagementTokenHash())) {
			throw new ForbiddenException();
		}
		if (shortUrl.isActive()) {
			repository.deactivate(code);
			cache.evict(code);
		}
		return toMetadata(shortUrl, false);
	}

	private String createAlias(LinkDraft draft, String alias) {
		if (repository.insertIfCodeAvailable(draft.toShortUrl(repository.nextId(), alias)) == 0) {
			throw new AliasTakenException(alias);
		}
		return alias;
	}

	private String createGenerated(LinkDraft draft) {
		for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
			long id = repository.nextId();
			String code = Base62.encode(id);
			// A custom alias may already hold, or concurrently take, a future generated code.
			if (!UrlValidator.isReserved(code) && repository.insertIfCodeAvailable(draft.toShortUrl(id, code)) == 1) {
				return code;
			}
		}
		throw new IllegalStateException("Unable to allocate a unique short code after 3 attempts");
	}

	private UrlMetadataResponse toMetadata(ShortUrl shortUrl, boolean active) {
		return new UrlMetadataResponse(
			shortUrl.getCode(),
			shortUrl(shortUrl.getCode()),
			shortUrl.getLongUrl(),
			shortUrl.getCreatedAt(),
			shortUrl.getExpiresAt(),
			shortUrl.getClickCount(),
			active,
			shortUrl.getRedirectType());
	}

	private String shortUrl(String code) {
		return baseUrl + "/" + code;
	}

	private static String stripTrailingSlashes(String value) {
		int end = value.length();
		while (end > 0 && value.charAt(end - 1) == '/') {
			end--;
		}
		return value.substring(0, end);
	}

	private record LinkDraft(
			String longUrl,
			Instant createdAt,
			Instant expiresAt,
			RedirectType redirectType,
			UUID ownerId,
			String managementTokenHash) {

		ShortUrl toShortUrl(long id, String code) {
			return new ShortUrl(id, code, longUrl, createdAt, expiresAt, redirectType, ownerId, managementTokenHash);
		}
	}
}
