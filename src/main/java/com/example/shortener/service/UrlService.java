package com.example.shortener.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shortener.api.dto.CreateUrlResponse;
import com.example.shortener.domain.Base62;
import com.example.shortener.domain.ShortUrl;
import com.example.shortener.repository.ShortUrlRepository;

@Service
public class UrlService {

	private final ShortUrlRepository repository;
	private final UrlValidator validator;
	private final String baseUrl;

	public UrlService(
			ShortUrlRepository repository,
			UrlValidator validator,
			@Value("${app.base-url}") String baseUrl) {
		this.repository = repository;
		this.validator = validator;
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

	private static String stripTrailingSlashes(String value) {
		int end = value.length();
		while (end > 0 && value.charAt(end - 1) == '/') {
			end--;
		}
		return value.substring(0, end);
	}
}
