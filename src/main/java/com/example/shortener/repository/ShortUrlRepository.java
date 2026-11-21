package com.example.shortener.repository;

import java.time.Instant;
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
			INSERT INTO urls (id, code, long_url, expires_at)
			VALUES (:id, :code, :longUrl, :expiresAt)
			ON CONFLICT (code) DO NOTHING
			""", nativeQuery = true)
	int insertIfCodeAvailable(
			@Param("id") long id,
			@Param("code") String code,
			@Param("longUrl") String longUrl,
			@Param("expiresAt") Instant expiresAt);
}
