package com.example.shortener;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
class ConcurrencyIntegrationTest {

	private static final int REQUEST_COUNT = 100;

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final HttpClient client = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.build();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void createsUniqueUrlsUnderConcurrentLoad() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(REQUEST_COUNT);
		CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<CreateResult>> futures = new ArrayList<>(REQUEST_COUNT);

		try {
			for (int index = 0; index < REQUEST_COUNT; index++) {
				int requestIndex = index;
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return createUrl(requestIndex);
				}));
			}

			assertTrue(ready.await(10, SECONDS), "All request threads should be ready");
			start.countDown();

			List<CreateResult> results = new ArrayList<>(REQUEST_COUNT);
			for (Future<CreateResult> future : futures) {
				results.add(future.get(30, SECONDS));
			}

			assertEquals(REQUEST_COUNT, results.stream()
				.filter(result -> result.statusCode() == 201)
				.count());
			Set<String> distinctCodes = new HashSet<>();
			results.forEach(result -> distinctCodes.add(result.code()));
			assertEquals(REQUEST_COUNT, distinctCodes.size());
			long rowCount = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM urls",
				Long.class);
			assertEquals(REQUEST_COUNT, rowCount);
		}
		finally {
			executor.shutdownNow();
		}
	}

	private CreateResult createUrl(int index) throws Exception {
		String body = "{\"url\":\"https://example.com/concurrent/" + index + "\"}";
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("http://localhost:" + port + "/api/v1/urls"))
			.timeout(Duration.ofSeconds(20))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		JsonNode responseBody = objectMapper.readTree(response.body());
		return new CreateResult(response.statusCode(), responseBody.path("code").asText());
	}

	private record CreateResult(int statusCode, String code) {
	}
}
