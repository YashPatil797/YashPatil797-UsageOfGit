package com.example.shortener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(
	classes = ShortenerApplication.class,
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class LinkManagementIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Container
	static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.data.redis.url",
			() -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
	}

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private StringRedisTemplate redisTemplate;

	private final HttpClient client = HttpClient.newHttpClient();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void resetState() {
		jdbc.update("DELETE FROM urls");
		redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
	}

	@Test
	void createReturnsOwnershipTokenAndMetadataNeverDoes() throws Exception {
		JsonNode created = json(create("{\"url\":\"https://example.com/a\",\"alias\":\"meta-link\"}", 201));

		assertEquals("meta-link", created.path("code").asText());
		assertEquals("https://example.com/a", created.path("originalUrl").asText());
		assertTrue(created.path("active").asBoolean());
		assertEquals(0, created.path("hitCount").asLong());
		assertEquals("temporary", created.path("redirectType").asText());
		assertEquals(43, created.path("managementToken").asText().length());

		HttpResponse<String> metadata = send("GET", "/api/v1/urls/meta-link", null, null);
		JsonNode body = json(metadata);
		assertEquals(200, metadata.statusCode());
		assertEquals(created.path("createdAt").asText(), body.path("createdAt").asText());
		assertFalse(body.has("managementToken"));
		assertFalse(metadata.body().contains(created.path("managementToken").asText()));
		assertFalse(metadata.body().toLowerCase().contains("hash"));
	}

	@Test
	void metadataReportsRedirectsWithoutCountingItself() throws Exception {
		create("{\"url\":\"https://example.com\",\"alias\":\"counted\"}", 201);

		assertEquals(302, send("GET", "/counted", null, null).statusCode());
		send("GET", "/api/v1/urls/counted", null, null);
		HttpResponse<String> metadata = send("GET", "/api/v1/urls/counted", null, null);

		assertEquals(1, json(metadata).path("hitCount").asLong());
		assertEquals(404, send("GET", "/api/v1/urls/unknown", null, null).statusCode());
	}

	@Test
	void permanentLinkRedirectsWith301FromDatabaseAndCache() throws Exception {
		create("{\"url\":\"https://example.com/new-home\",\"alias\":\"moved\",\"redirectType\":\"permanent\"}", 201);

		for (int attempt = 0; attempt < 2; attempt++) {
			HttpResponse<String> redirect = send("GET", "/moved", null, null);
			assertEquals(301, redirect.statusCode());
			assertEquals("https://example.com/new-home", redirect.headers().firstValue("Location").orElseThrow());
		}
		assertTrue(redisTemplate.hasKey("url:v2:moved"));
		assertEquals("permanent", json(send("GET", "/api/v1/urls/moved", null, null)).path("redirectType").asText());
	}

	@Test
	void unknownRedirectTypeIsRejected() throws Exception {
		create("{\"url\":\"https://example.com\",\"redirectType\":\"forever\"}", 400);
	}

	@Test
	void ownerCanDeactivateAndLinkStaysReserved() throws Exception {
		String token = json(create("{\"url\":\"https://example.com\",\"alias\":\"promo\"}", 201))
			.path("managementToken").asText();
		assertEquals(302, send("GET", "/promo", null, null).statusCode());
		assertTrue(redisTemplate.hasKey("url:v2:promo"));
		String deactivate = "{\"active\":false}";

		HttpResponse<String> unauthenticated = send("PATCH", "/api/v1/urls/promo", deactivate, null);
		assertEquals(401, unauthenticated.statusCode());
		assertEquals("Bearer", unauthenticated.headers().firstValue("WWW-Authenticate").orElseThrow());
		assertEquals(403, send("PATCH", "/api/v1/urls/promo", deactivate, "Bearer wrong-token").statusCode());

		HttpResponse<String> deactivated = send("PATCH", "/api/v1/urls/promo", deactivate, "Bearer " + token);
		assertEquals(200, deactivated.statusCode());
		assertFalse(json(deactivated).path("active").asBoolean());
		assertEquals(1, json(deactivated).path("hitCount").asLong());
		assertFalse(redisTemplate.hasKey("url:v2:promo"));

		HttpResponse<String> gone = send("GET", "/promo", null, null);
		assertEquals(410, gone.statusCode());
		assertEquals("inactive", json(gone).path("error").asText());
		assertEquals(1L, jdbc.queryForObject("SELECT click_count FROM urls WHERE code = 'promo'", Long.class));
		create("{\"url\":\"https://attacker.example\",\"alias\":\"promo\"}", 409);
		assertEquals(200, send("PATCH", "/api/v1/urls/promo", deactivate, "Bearer " + token).statusCode());
	}

	@Test
	void staleCacheEntryCannotRedirectDeactivatedLink() throws Exception {
		create("{\"url\":\"https://example.com\",\"alias\":\"stale\"}", 201);
		assertEquals(302, send("GET", "/stale", null, null).statusCode());
		jdbc.update("UPDATE urls SET active = false WHERE code = 'stale'");

		assertTrue(redisTemplate.hasKey("url:v2:stale"));
		assertEquals(410, send("GET", "/stale", null, null).statusCode());
	}

	@Test
	void deactivationRejectsReactivationUnknownCodesAndLegacyLinks() throws Exception {
		String token = json(create("{\"url\":\"https://example.com\",\"alias\":\"keeper\"}", 201))
			.path("managementToken").asText();
		jdbc.update("INSERT INTO urls (id, code, long_url) VALUES (1, 'legacy', 'https://example.com')");

		assertEquals(400, send("PATCH", "/api/v1/urls/keeper", "{\"active\":true}", "Bearer " + token).statusCode());
		assertEquals(404, send("PATCH", "/api/v1/urls/missing", "{\"active\":false}", "Bearer " + token).statusCode());
		assertEquals(403, send("PATCH", "/api/v1/urls/legacy", "{\"active\":false}", "Bearer " + token).statusCode());
	}

	private HttpResponse<String> create(String body, int expectedStatus) throws Exception {
		HttpResponse<String> response = send("POST", "/api/v1/urls", body, null);
		assertEquals(expectedStatus, response.statusCode(), response.body());
		return response;
	}

	private HttpResponse<String> send(String method, String path, String body, String authorization)
			throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
			.method(method, body == null
					? HttpRequest.BodyPublishers.noBody()
					: HttpRequest.BodyPublishers.ofString(body));
		if (body != null) {
			request.header("Content-Type", "application/json");
		}
		if (authorization != null) {
			request.header("Authorization", authorization);
		}
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private JsonNode json(HttpResponse<String> response) throws Exception {
		return objectMapper.readTree(response.body());
	}
}
