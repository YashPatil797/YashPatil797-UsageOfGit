package com.example.shortener.api.dto;

import java.time.Instant;

import com.example.shortener.domain.RedirectType;

public record CreateUrlResponse(
		String code,
		String shortUrl,
		String originalUrl,
		Instant createdAt,
		Instant expiresAt,
		long hitCount,
		boolean active,
		RedirectType redirectType,
		String managementToken) {
}
