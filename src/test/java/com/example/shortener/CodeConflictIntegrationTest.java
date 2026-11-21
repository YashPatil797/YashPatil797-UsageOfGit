package com.example.shortener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(
	classes = ShortenerApplication.class,
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class CodeConflictIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private JdbcTemplate jdbc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void resetDatabase() {
		jdbc.update("DELETE FROM urls");
		jdbc.queryForObject("SELECT setval('url_id_seq', 238328, false)", Long.class);
	}

	@Test
	void generatedCodeSkipsCodeAlreadyHeldByAlias() throws Exception {
		jdbc.update("INSERT INTO urls (id, code, long_url) VALUES (1, '1000', 'https://alias.example')");

		ResponseEntity<String> response = create(Map.of("url", "https://example.com"));

		assertEquals(201, response.getStatusCode().value());
		assertEquals("1001", objectMapper.readTree(response.getBody()).path("code").asText());
		assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM urls", Long.class));
	}

	@Test
	void duplicateAliasReturnsConflict() {
		assertEquals(201, create(Map.of("url", "https://example.com", "alias", "my-sale")).getStatusCode().value());

		ResponseEntity<String> duplicate = create(Map.of("url", "https://other.example", "alias", "my-sale"));

		assertEquals(409, duplicate.getStatusCode().value());
		assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM urls", Long.class));
	}

	@Test
	void aliasWithExpiryIsStored() throws Exception {
		ResponseEntity<String> response = create(
			Map.of("url", "https://example.com", "alias", "spring-sale", "expiresInDays", 30));

		assertEquals(201, response.getStatusCode().value());
		JsonNode body = objectMapper.readTree(response.getBody());
		assertEquals("spring-sale", body.path("code").asText());
		assertNotNull(jdbc.queryForObject(
			"SELECT expires_at FROM urls WHERE code = 'spring-sale'", java.sql.Timestamp.class));
	}

	private ResponseEntity<String> create(Map<String, Object> body) {
		return rest.postForEntity("/api/v1/urls", body, String.class);
	}
}
