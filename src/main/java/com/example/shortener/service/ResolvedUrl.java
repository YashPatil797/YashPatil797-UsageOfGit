package com.example.shortener.service;

import com.example.shortener.domain.RedirectType;

public record ResolvedUrl(String url, RedirectType redirectType) {
}
