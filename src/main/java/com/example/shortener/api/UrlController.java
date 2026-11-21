package com.example.shortener.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shortener.api.dto.CreateUrlRequest;
import com.example.shortener.api.dto.CreateUrlResponse;
import com.example.shortener.api.dto.UpdateUrlRequest;
import com.example.shortener.api.dto.UrlMetadataResponse;
import com.example.shortener.service.UnauthorizedException;
import com.example.shortener.service.UrlService;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

	private static final String BEARER_PREFIX = "Bearer ";

	private final UrlService urlService;

	public UrlController(UrlService urlService) {
		this.urlService = urlService;
	}

	@PostMapping
	public ResponseEntity<CreateUrlResponse> create(@RequestBody CreateUrlRequest request) {
		CreateUrlResponse response = urlService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping("/{code:[A-Za-z0-9_-]{1,30}}")
	public UrlMetadataResponse metadata(@PathVariable String code) {
		return urlService.metadata(code);
	}

	@PatchMapping("/{code:[A-Za-z0-9_-]{1,30}}")
	public UrlMetadataResponse update(
			@PathVariable String code,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@RequestBody UpdateUrlRequest request) {
		String token = bearerToken(authorization);
		if (!Boolean.FALSE.equals(request.active())) {
			throw new IllegalArgumentException("Only {\"active\": false} is supported");
		}
		return urlService.deactivate(code, token);
	}

	private static String bearerToken(String authorization) {
		if (authorization == null
				|| !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
				|| authorization.substring(BEARER_PREFIX.length()).isBlank()) {
			throw new UnauthorizedException();
		}
		return authorization.substring(BEARER_PREFIX.length()).trim();
	}
}
