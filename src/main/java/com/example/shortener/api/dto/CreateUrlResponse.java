package com.example.shortener.api.dto;

import java.time.Instant;

public record CreateUrlResponse(String code, String shortUrl, Instant expiresAt) {
}
