package com.example.shortener.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	// Changed only through atomic repository updates; a stale entity flush must never overwrite them.
	@Column(name = "click_count", nullable = false, insertable = false, updatable = false)
	private long clickCount;

	@Column(name = "active", nullable = false, insertable = false, updatable = false)
	private boolean active;

	@Enumerated(EnumType.STRING)
	@Column(name = "redirect_type", nullable = false, length = 16)
	private RedirectType redirectType;

	@Column(name = "owner_id")
	private UUID ownerId;

	@Column(name = "management_token_hash", length = 64)
	private String managementTokenHash;

	protected ShortUrl() {
	}

	public ShortUrl(Long id, String code, String longUrl, Instant expiresAt) {
		this(id, code, longUrl, Instant.now(), expiresAt, RedirectType.TEMPORARY, null, null);
	}

	public ShortUrl(
			Long id,
			String code,
			String longUrl,
			Instant createdAt,
			Instant expiresAt,
			RedirectType redirectType,
			UUID ownerId,
			String managementTokenHash) {
		this.id = id;
		this.code = code;
		this.longUrl = longUrl;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
		this.active = true;
		this.redirectType = redirectType;
		this.ownerId = ownerId;
		this.managementTokenHash = managementTokenHash;
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

	public boolean isActive() {
		return active;
	}

	public RedirectType getRedirectType() {
		return redirectType;
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public String getManagementTokenHash() {
		return managementTokenHash;
	}
}
