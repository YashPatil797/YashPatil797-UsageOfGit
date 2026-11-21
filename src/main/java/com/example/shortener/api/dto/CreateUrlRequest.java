package com.example.shortener.api.dto;

public record CreateUrlRequest(String url, String alias, Integer expiresInDays, String redirectType) {
}
