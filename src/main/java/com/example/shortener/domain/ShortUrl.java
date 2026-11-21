package com.example.shortener.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "urls")
public class ShortUrl {

	@Id
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "code", nullable = false, unique = true, length = 30)
	private String code;

	@Column(name = "long_url", nullable = false, columnDefinition = "TEXT")
	private String longUrl;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "click_count", nullable = false, insertable = false)
	private long clickCount;

	protected ShortUrl() {
	}

	public ShortUrl(Long id, String code, String longUrl, Instant expiresAt) {
		this.id = id;
		this.code = code;
		this.longUrl = longUrl;
		this.expiresAt = expiresAt;
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getLongUrl() {
		return longUrl;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public long getClickCount() {
		return clickCount;
	}
}
