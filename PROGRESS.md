# URL Shortener Progress

Each slice is scoped to 10–15 minutes and must leave the application runnable.

## Skeleton (S)

- [x] **S1 — Spring Boot walking skeleton**
  - Covers: FR-9
  - Files: `pom.xml`, `src/main/java/com/example/shortener/ShortenerApplication.java`, `src/main/resources/application.yml`, `PROGRESS.md`
  - Verify: `mvn -q compile`

- [x] **S2 — Docker and two-instance Compose stack**
  - Covers: NFR-7, NFR-11
  - Files: `Dockerfile`, `docker-compose.yml`, `.gitignore`, `.dockerignore`, `PROGRESS.md`
  - Verify: `docker compose up --build -d && curl localhost:8081/actuator/health && curl localhost:8082/actuator/health`

## Core (C)

- [x] **C1 — Schema, entity, and repository**
  - Covers: NFR-1, NFR-6
  - Files: `src/main/resources/db/migration/V1__create_urls.sql`, `src/main/java/com/example/shortener/domain/ShortUrl.java`, `src/main/java/com/example/shortener/repository/ShortUrlRepository.java`, `PROGRESS.md`
  - Verify: `docker compose up --build -d && docker compose exec postgres psql -U shortener -c '\d urls' && docker compose logs app-1 | grep -i flyway`

- [x] **C2 — Base62 encoder**
  - Covers: NFR-4
  - Files: `src/main/java/com/example/shortener/domain/Base62.java`, `src/test/java/com/example/shortener/domain/Base62Test.java`, `PROGRESS.md`
  - Verify: `mvn -q test -Dtest=Base62Test`

- [x] **C3 — Create short URL**
  - Covers: FR-1, FR-3, FR-7
  - Files: `src/main/java/com/example/shortener/api/UrlController.java`, `src/main/java/com/example/shortener/api/dto/CreateUrlRequest.java`, `src/main/java/com/example/shortener/api/dto/CreateUrlResponse.java`, `src/main/java/com/example/shortener/service/UrlService.java`, `src/main/java/com/example/shortener/service/UrlValidator.java`, `src/main/java/com/example/shortener/api/GlobalExceptionHandler.java`, `PROGRESS.md`
  - Verify: `curl -s -X POST localhost:8081/api/v1/urls -H 'Content-Type: application/json' -d '{"url":"https://example.com/a/very/long/path"}' && curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8081/api/v1/urls -H 'Content-Type: application/json' -d '{"url":"javascript:alert(1)"}'`

- [x] **C4 — Redirect**
  - Covers: FR-2, FR-4
  - Files: `src/main/java/com/example/shortener/api/RedirectController.java`, `src/main/java/com/example/shortener/service/UrlService.java`, `src/main/java/com/example/shortener/service/NotFoundException.java`, `src/main/java/com/example/shortener/api/GlobalExceptionHandler.java`, `PROGRESS.md`
  - Verify: `CODE=$(curl -s -X POST localhost:8081/api/v1/urls -H 'Content-Type: application/json' -d '{"url":"https://example.com"}' | sed -E 's/.*"code":"([^"]+)".*/\1/') && curl -i localhost:8082/$CODE && curl -i localhost:8081/nope123`

- [x] **C5 — Redis cache-aside on redirect**
  - Covers: NFR-2, NFR-5, NFR-9
  - Files: `src/main/java/com/example/shortener/cache/UrlCache.java`, `src/main/java/com/example/shortener/service/UrlService.java`, `PROGRESS.md`
  - Verify: `curl -s -o /dev/null localhost:8081/$CODE && docker compose exec redis redis-cli GET url:$CODE && docker compose exec redis redis-cli TTL url:$CODE && docker compose stop redis && curl -i localhost:8081/$CODE && docker compose start redis`

- [x] **C6 — Custom alias and expiry**
  - Covers: FR-5, FR-6
  - Files: `src/main/java/com/example/shortener/api/dto/CreateUrlRequest.java`, `src/main/java/com/example/shortener/api/UrlController.java`, `src/main/java/com/example/shortener/service/UrlService.java`, `src/main/java/com/example/shortener/service/UrlValidator.java`, `src/main/java/com/example/shortener/service/AliasTakenException.java`, `src/main/java/com/example/shortener/service/ExpiredException.java`, `src/main/java/com/example/shortener/api/GlobalExceptionHandler.java`, `PROGRESS.md`
  - Verify: `curl -s -X POST localhost:8081/api/v1/urls -H 'Content-Type: application/json' -d '{"url":"https://example.com","alias":"my-sale"}' && curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8081/api/v1/urls -H 'Content-Type: application/json' -d '{"url":"https://x.com","alias":"my-sale"}' && curl -i localhost:8081/my-sale`

- [ ] **C7 — Redirect and atomic hit counting**
  - Covers: FR-3, FR-4, NFR-2, NFR-3
  - Files: `src/main/java/com/example/shortener/api/RedirectController.java`, `src/main/java/com/example/shortener/service/RedirectService.java`, `src/main/java/com/example/shortener/repository/ShortUrlRepository.java`, `src/test/java/com/example/shortener/api/RedirectApiTest.java`
  - Verify: `./mvnw -Dtest=RedirectApiTest test`

- [ ] **C8 — Redis read-through cache and eviction**
  - Covers: FR-3, FR-4, NFR-2, NFR-3
  - Files: `src/main/java/com/example/shortener/config/RedisConfig.java`, `src/main/java/com/example/shortener/cache/CachedUrl.java`, `src/main/java/com/example/shortener/cache/UrlCache.java`, `src/main/java/com/example/shortener/service/RedirectService.java`, `src/main/java/com/example/shortener/service/UrlService.java`, `src/test/java/com/example/shortener/cache/UrlCacheIntegrationTest.java`
  - Verify: `./mvnw -Dtest=UrlCacheIntegrationTest test`

## Tests (T)

- [x] **T1 — Unit tests**
  - Covers: FR-3, NFR-4
  - Files: `src/test/java/com/example/shortener/service/UrlValidatorTest.java`, `src/test/java/com/example/shortener/service/UrlServiceTest.java`, `PROGRESS.md`
  - Verify: `mvn -q test -Dtest='UrlValidatorTest,UrlServiceTest'`

- [ ] **T2 — Concurrency and acceptance suite**
  - Covers: FR-1, FR-2, FR-3, FR-4, FR-5, NFR-2, NFR-3
  - Files: `src/test/java/com/example/shortener/UrlShortenerAcceptanceTest.java`, `src/test/java/com/example/shortener/repository/HitCountConcurrencyTest.java`
  - Verify: `./mvnw test`

## Deploy (D)

- [ ] **D1 — Runnable local container stack**
  - Covers: FR-5, NFR-2, NFR-4
  - Files: `Dockerfile`, `.dockerignore`, `compose.yaml`, `src/main/resources/application.properties`
  - Verify: `docker compose up --build -d --wait && curl -fsS http://localhost:8080/health`

- [ ] **D2 — DigitalOcean App Platform specification**
  - Covers: NFR-2, NFR-4
  - Files: `.do/app.yaml`, `README.md`
  - Verify: `doctl apps spec validate .do/app.yaml`

## Wrap-up (W)

- [ ] **W1 — API and operations documentation**
  - Covers: FR-1, FR-2, FR-3, FR-4, FR-5, NFR-1, NFR-4
  - Files: `README.md`, `HIGH_LEVEL_DESIGN.md`
  - Verify: `./mvnw test`

- [ ] **W2 — Final acceptance and clean build**
  - Covers: FR-1, FR-2, FR-3, FR-4, FR-5, NFR-1, NFR-2, NFR-3, NFR-4
  - Files: `PROGRESS.md`
  - Verify: `docker compose up --build -d --wait && ./mvnw clean verify && curl -fsS http://localhost:8080/health`

## NEXT

T2 — Concurrency and acceptance suite. Do not begin automatically.

## Log

- S2: Added a non-root, memory-aware multi-stage image and a Compose stack with PostgreSQL 16, Redis 7, and two application instances on ports 8081/8082. The existing `V1__create_short_urls.sql` migration supplies the Flyway schema, so no empty baseline migration was needed.
- S1: Added the Spring Boot 3.3/Java 21 walking-skeleton entry point and YAML configuration for PostgreSQL, Redis, JPA validation, base URL, port, and detailed health reporting. Configured the Boot plugin to package `ShortenerApplication`. Legacy `UrlShortenerApplication.java` and `application.properties` remain untouched because they were outside this slice's allowed file list.
- S2 revalidation: Confirmed the Dockerfile, two-instance Compose model, YAML syntax, and packaged JAR after S1. Docker and curl health checks remain pending because Docker is not installed on this VM.
- C1: Replaced the earlier V1 schema with `url_id_seq` and `urls`, added the assigned-ID JPA entity, and added code lookup plus sequence access in the repository. Compilation passes; PostgreSQL/Flyway runtime verification is pending because Docker is not installed on this VM.
- C2: Added dependency-free Base62 encoding and decoding with validation and overflow protection. Required boundary and round-trip tests pass with the Maven wrapper; system Maven is unavailable on this VM.
- C3: Added URL validation, transactional sequence-backed creation, Base62 code generation, the `POST /api/v1/urls` contract, and safe JSON error responses. Compilation passes; curl integration verification is pending because Docker is unavailable.
- C4: Added a reserved-prefix-safe root redirect route, cross-instance database resolution, explicit `302 Found`, and JSON `404` handling. Compilation passes; two-instance curl verification is pending because Docker is unavailable.
- C5: Added Redis cache-aside resolution with the required key/value format, expiration-aware TTL capped at 24 hours, DEBUG hit/miss logging, and WARN-plus-database fallback for every Redis failure. Compilation passes; Redis integration verification is pending because Docker is unavailable.
- C6: Added validated aliases and 1–365 day expiry, duplicate-alias `409`, generated-code collision retries, and expired-link `410` behavior on cached and database resolution paths. The user approved the required `UrlController` integration edit. Compilation passes; curl verification is pending because Docker is unavailable.
- T1: Added no-context unit tests for URL and alias validation, sequence/Base62 creation, not-found and expired resolution, and Redis failure fallback. All requested tests pass with the Maven wrapper; system Maven is unavailable.
