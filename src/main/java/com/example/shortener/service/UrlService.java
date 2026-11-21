package com.example.shortener.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shortener.api.dto.CreateUrlRequest;
import com.example.shortener.api.dto.CreateUrlResponse;
import com.example.shortener.cache.UrlCache;
import com.example.shortener.domain.Base62;
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

		Instant expiresAt = request.expiresInDays() == null
				? null
				: Instant.now().plus(request.expiresInDays(), ChronoUnit.DAYS);
		ShortUrl shortUrl = request.alias() == null
				? createGenerated(request.url(), expiresAt)
				: createAlias(request.url(), request.alias(), expiresAt);

		return new CreateUrlResponse(
			shortUrl.getCode(),
			baseUrl + "/" + shortUrl.getCode(),
			shortUrl.getExpiresAt());
	}

	@Transactional(readOnly = true)
	public String resolve(String code) {
		Optional<String> cachedUrl = cache.get(code);
		if (cachedUrl.isPresent()) {
			// UrlCache rejects entries whose embedded expiry is in the past.
			LOGGER.debug("cache hit for code {}", code);
			return cachedUrl.get();
		}
		LOGGER.debug("cache miss for code {}", code);

		Instant now = Instant.now();
		ShortUrl shortUrl = repository.findByCode(code)
			.orElseThrow(() -> new NotFoundException("Short URL not found"));
		if (shortUrl.getExpiresAt() != null && !shortUrl.getExpiresAt().isAfter(now)) {
			throw new ExpiredException(code);
		}

		cache.put(code, shortUrl.getLongUrl(), shortUrl.getExpiresAt());
		return shortUrl.getLongUrl();
	}

	private ShortUrl createAlias(String longUrl, String alias, Instant expiresAt) {
		long id = repository.nextId();
		try {
			return repository.saveAndFlush(new ShortUrl(id, alias, longUrl, expiresAt));
		}
		catch (DataIntegrityViolationException exception) {
			throw new AliasTakenException(alias);
		}
	}

	private ShortUrl createGenerated(String longUrl, Instant expiresAt) {
		for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
			long id = repository.nextId();
			String code = Base62.encode(id);
			// A custom alias may equal a future generated code, so occupied codes must be skipped.
			if (repository.findByCode(code).isEmpty()) {
				return repository.save(new ShortUrl(id, code, longUrl, expiresAt));
			}
		}
		throw new IllegalStateException("Unable to allocate a unique short code after 3 attempts");
	}

	private static String stripTrailingSlashes(String value) {
		int end = value.length();
		while (end > 0 && value.charAt(end - 1) == '/') {
			end--;
		}
		return value.substring(0, end);
	}
}
