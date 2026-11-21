package com.example.shortener.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.shortener.domain.ShortUrl;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

	Optional<ShortUrl> findByCode(String code);

	@Query(value = "SELECT nextval('url_id_seq')", nativeQuery = true)
	long nextId();

	// Returns 0 instead of failing when the code is taken, so the transaction stays usable for a retry.
	@Modifying
	@Query(value = """
			INSERT INTO urls (id, code, long_url, created_at, expires_at, redirect_type, owner_id, management_token_hash)
			VALUES (:#{#url.id}, :#{#url.code}, :#{#url.longUrl}, :#{#url.createdAt}, :#{#url.expiresAt},
			        :#{#url.redirectType.name()}, :#{#url.ownerId}, :#{#url.managementTokenHash})
			ON CONFLICT (code) DO NOTHING
			""", nativeQuery = true)
	int insertIfCodeAvailable(@Param("url") ShortUrl url);

	@Modifying
	@Query(value = """
			UPDATE urls SET click_count = click_count + 1
			WHERE code = :code AND active AND (expires_at IS NULL OR expires_at > now())
			""", nativeQuery = true)
	int incrementClickCount(@Param("code") String code);

	@Modifying
	@Query(value = "UPDATE urls SET active = false WHERE code = :code", nativeQuery = true)
	int deactivate(@Param("code") String code);
}
