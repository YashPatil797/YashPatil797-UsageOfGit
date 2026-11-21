package com.example.shortener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
class HealthEndpointIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		// Port 1 is never a Redis server, so the cache is down for this whole test.
		registry.add("spring.data.redis.url", () -> "redis://localhost:1");
	}

	@LocalServerPort
	private int port;

	private final HttpClient client = HttpClient.newHttpClient();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void healthIsUpWithoutDetailsWhileRedisIsDown() throws Exception {
		HttpResponse<String> response = send("GET", "/health", null);

		assertEquals(200, response.statusCode());
		JsonNode body = objectMapper.readTree(response.body());
		assertEquals("UP", body.path("status").asText());
		assertFalse(body.has("components"), "Component details must not be exposed publicly");
	}

	@Test
	void actuatorPrefixDoesNotServeHealth() throws Exception {
		assertEquals(404, send("GET", "/actuator/health", null).statusCode());
	}

	@Test
	void healthCannotBeClaimedAsAlias() throws Exception {
		HttpResponse<String> response = send(
			"POST", "/api/v1/urls", "{\"url\":\"https://example.com\",\"alias\":\"health\"}");

		assertEquals(400, response.statusCode());
	}

	private HttpResponse<String> send(String method, String path, String body) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
		if (body != null) {
			request.header("Content-Type", "application/json")
				.method(method, HttpRequest.BodyPublishers.ofString(body));
		}
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}
}
