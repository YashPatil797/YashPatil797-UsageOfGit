package com.example.shortener.service;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shortener.api.dto.CreateUrlResponse;
import com.example.shortener.cache.UrlCache;
import com.example.shortener.domain.Base62;
import com.example.shortener.domain.ShortUrl;
import com.example.shortener.repository.ShortUrlRepository;

@Service
public class UrlService {

	private static final Logger LOGGER = LoggerFactory.getLogger(UrlService.class);

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
	public CreateUrlResponse create(String longUrl) {
		validator.validate(longUrl);

		long id = repository.nextId();
		String code = Base62.encode(id);
		ShortUrl shortUrl = repository.save(new ShortUrl(id, code, longUrl, null));

		return new CreateUrlResponse(code, baseUrl + "/" + code, shortUrl.getExpiresAt());
	}

	@Transactional(readOnly = true)
	public String resolve(String code) {
		Optional<String> cachedUrl = cache.get(code);
		if (cachedUrl.isPresent()) {
			LOGGER.debug("cache hit for code {}", code);
			return cachedUrl.get();
		}
		LOGGER.debug("cache miss for code {}", code);

		Instant now = Instant.now();
		ShortUrl shortUrl = repository.findByCode(code)
			.filter(url -> url.getExpiresAt() == null || url.getExpiresAt().isAfter(now))
			.orElseThrow(() -> new NotFoundException("Short URL not found"));

		cache.put(code, shortUrl.getLongUrl(), shortUrl.getExpiresAt());
		return shortUrl.getLongUrl();
	}

	private static String stripTrailingSlashes(String value) {
		int end = value.length();
		while (end > 0 && value.charAt(end - 1) == '/') {
			end--;
		}
		return value.substring(0, end);
	}
}
