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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

@SpringBootTest(
	classes = ShortenerApplication.class,
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class ClickCountIntegrationTest {

	private static final int REDIRECT_COUNT = 50;

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Container
	static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
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

	private final HttpClient client = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.build();

	@BeforeEach
	void resetState() {
		jdbc.update("DELETE FROM urls");
		redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
	}

	@Test
	void concurrentCachedRedirectsAreEachCountedOnce() throws Exception {
		jdbc.update("INSERT INTO urls (id, code, long_url) VALUES (1, 'popular', 'https://example.com')");
		assertEquals(302, get("/popular"));
		assertTrue(redisTemplate.hasKey("url:popular"), "First redirect should populate the cache");

		ExecutorService executor = Executors.newFixedThreadPool(REDIRECT_COUNT);
		CountDownLatch ready = new CountDownLatch(REDIRECT_COUNT);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> futures = new ArrayList<>(REDIRECT_COUNT);

		try {
			for (int index = 0; index < REDIRECT_COUNT; index++) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return get("/popular");
				}));
			}
			assertTrue(ready.await(10, SECONDS), "All request threads should be ready");
			start.countDown();

			for (Future<Integer> future : futures) {
				assertEquals(302, future.get(30, SECONDS));
			}
		}
		finally {
			executor.shutdownNow();
		}

		assertEquals(REDIRECT_COUNT + 1, clickCount("popular"));
	}

	@Test
	void cachedLinkDeletedFromDatabaseIsNotRedirected() throws Exception {
		jdbc.update("INSERT INTO urls (id, code, long_url) VALUES (1, 'gone', 'https://example.com')");
		assertEquals(302, get("/gone"));
		jdbc.update("DELETE FROM urls WHERE code = 'gone'");

		assertEquals(404, get("/gone"));
	}

	@Test
	void expiredAndUnknownCodesAreNotCounted() throws Exception {
		jdbc.update("""
				INSERT INTO urls (id, code, long_url, expires_at)
				VALUES (1, 'expired', 'https://example.com', now() - interval '1 minute')
				""");

		assertEquals(410, get("/expired"));
		assertEquals(404, get("/unknown"));
		assertEquals(0L, clickCount("expired"));
	}

	private int get(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("http://localhost:" + port + path))
			.timeout(Duration.ofSeconds(20))
			.GET()
			.build();
		return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	private long clickCount(String code) {
		return jdbc.queryForObject("SELECT click_count FROM urls WHERE code = ?", Long.class, code);
	}
}
