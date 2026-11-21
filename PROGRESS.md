# URL Shortener Progress

Each slice is scoped to 10–15 minutes and must leave the application runnable.

## Skeleton (S)

- [x] **S1 — Project foundation**
  - Covers: NFR-2, NFR-4
  - Files: `pom.xml`, `Dockerfile`, `compose.yaml`, `src/main/resources/application.properties`, `src/main/resources/db/migration/V1__create_short_urls.sql`, `src/main/java/com/example/shortener/UrlShortenerApplication.java`
  - Verify: `./mvnw test`

- [ ] **S2 — Typed configuration and health contract**
  - Covers: FR-5, NFR-2, NFR-4
  - Files: `src/main/java/com/example/shortener/config/ShortenerProperties.java`, `src/main/resources/application.properties`, `src/test/java/com/example/shortener/api/HealthEndpointTest.java`
  - Verify: `./mvnw -Dtest=HealthEndpointTest test`

## Core (C)

- [ ] **C1 — Persistent URL domain and repository**
  - Covers: FR-1, FR-4, NFR-2
  - Files: `src/main/java/com/example/shortener/domain/ShortUrl.java`, `src/main/java/com/example/shortener/domain/RedirectType.java`, `src/main/java/com/example/shortener/repository/ShortUrlRepository.java`, `src/test/java/com/example/shortener/repository/ShortUrlRepositoryTest.java`
  - Verify: `./mvnw -Dtest=ShortUrlRepositoryTest test`

- [ ] **C2 — Automatic short-code generation**
  - Covers: FR-1, NFR-1
  - Files: `src/main/java/com/example/shortener/service/ShortCodeGenerator.java`, `src/test/java/com/example/shortener/service/ShortCodeGeneratorTest.java`
  - Verify: `./mvnw -Dtest=ShortCodeGeneratorTest test`

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

S2 — Typed configuration and health contract.

## Log
