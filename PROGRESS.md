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

- [ ] **C3 — Create-short-URL endpoint**
  - Covers: FR-1, NFR-1
  - Files: `src/main/java/com/example/shortener/api/CreateUrlRequest.java`, `src/main/java/com/example/shortener/api/UrlResponse.java`, `src/main/java/com/example/shortener/api/UrlController.java`, `src/main/java/com/example/shortener/service/UrlService.java`, `src/test/java/com/example/shortener/api/CreateUrlApiTest.java`
  - Verify: `./mvnw -Dtest=CreateUrlApiTest test`

- [ ] **C4 — Custom aliases and conflict handling**
  - Covers: FR-2, NFR-1
  - Files: `src/main/java/com/example/shortener/service/AliasPolicy.java`, `src/main/java/com/example/shortener/service/UrlService.java`, `src/main/java/com/example/shortener/api/UrlController.java`, `src/test/java/com/example/shortener/api/CustomAliasApiTest.java`
  - Verify: `./mvnw -Dtest=CustomAliasApiTest test`

- [ ] **C5 — Metadata lookup**
  - Covers: FR-4, NFR-3
  - Files: `src/main/java/com/example/shortener/api/UrlResponse.java`, `src/main/java/com/example/shortener/api/UrlController.java`, `src/main/java/com/example/shortener/service/UrlService.java`, `src/test/java/com/example/shortener/api/MetadataApiTest.java`
  - Verify: `./mvnw -Dtest=MetadataApiTest test`

- [ ] **C6 — Management token and deactivation**
  - Covers: FR-4, NFR-1
  - Files: `src/main/java/com/example/shortener/service/ManagementTokenService.java`, `src/main/java/com/example/shortener/api/UpdateUrlRequest.java`, `src/main/java/com/example/shortener/api/UrlController.java`, `src/main/java/com/example/shortener/service/UrlService.java`, `src/test/java/com/example/shortener/api/DeactivationApiTest.java`
  - Verify: `./mvnw -Dtest=DeactivationApiTest test`

- [ ] **C7 — Redirect and atomic hit counting**
  - Covers: FR-3, FR-4, NFR-2, NFR-3
  - Files: `src/main/java/com/example/shortener/api/RedirectController.java`, `src/main/java/com/example/shortener/service/RedirectService.java`, `src/main/java/com/example/shortener/repository/ShortUrlRepository.java`, `src/test/java/com/example/shortener/api/RedirectApiTest.java`
  - Verify: `./mvnw -Dtest=RedirectApiTest test`

- [ ] **C8 — Redis read-through cache and eviction**
  - Covers: FR-3, FR-4, NFR-2, NFR-3
  - Files: `src/main/java/com/example/shortener/config/RedisConfig.java`, `src/main/java/com/example/shortener/cache/CachedUrl.java`, `src/main/java/com/example/shortener/cache/UrlCache.java`, `src/main/java/com/example/shortener/service/RedirectService.java`, `src/main/java/com/example/shortener/service/UrlService.java`, `src/test/java/com/example/shortener/cache/UrlCacheIntegrationTest.java`
  - Verify: `./mvnw -Dtest=UrlCacheIntegrationTest test`

## Tests (T)

- [ ] **T1 — Consistent validation and error responses**
  - Covers: FR-1, FR-2, FR-3, FR-4, NFR-1
  - Files: `src/main/java/com/example/shortener/api/ApiError.java`, `src/main/java/com/example/shortener/api/GlobalExceptionHandler.java`, `src/test/java/com/example/shortener/api/ApiErrorContractTest.java`
  - Verify: `./mvnw -Dtest=ApiErrorContractTest test`

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

C3 — Create-short-URL endpoint. Do not begin automatically.

## Log

- S2: Added a non-root, memory-aware multi-stage image and a Compose stack with PostgreSQL 16, Redis 7, and two application instances on ports 8081/8082. The existing `V1__create_short_urls.sql` migration supplies the Flyway schema, so no empty baseline migration was needed.
- S1: Added the Spring Boot 3.3/Java 21 walking-skeleton entry point and YAML configuration for PostgreSQL, Redis, JPA validation, base URL, port, and detailed health reporting. Configured the Boot plugin to package `ShortenerApplication`. Legacy `UrlShortenerApplication.java` and `application.properties` remain untouched because they were outside this slice's allowed file list.
- S2 revalidation: Confirmed the Dockerfile, two-instance Compose model, YAML syntax, and packaged JAR after S1. Docker and curl health checks remain pending because Docker is not installed on this VM.
- C1: Replaced the earlier V1 schema with `url_id_seq` and `urls`, added the assigned-ID JPA entity, and added code lookup plus sequence access in the repository. Compilation passes; PostgreSQL/Flyway runtime verification is pending because Docker is not installed on this VM.
- C2: Added dependency-free Base62 encoding and decoding with validation and overflow protection. Required boundary and round-trip tests pass with the Maven wrapper; system Maven is unavailable on this VM.
