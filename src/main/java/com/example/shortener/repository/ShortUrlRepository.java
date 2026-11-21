package com.example.shortener.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.shortener.domain.ShortUrl;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

	Optional<ShortUrl> findByCode(String code);

	@Query(value = "SELECT nextval('url_id_seq')", nativeQuery = true)
	long nextId();
}
