# URL Shortener Progress

## Slices

- [x] Foundation stack: Java 21, Spring Boot 3.3.x, Maven, PostgreSQL 16, Redis 7, Flyway, Docker, JUnit 5, and Testcontainers.
- [ ] FR-1: Create a short URL.
- [ ] FR-2: Custom alias.
- [ ] FR-3: Redirect a short URL.
- [ ] FR-4: Metadata and link status.
- [ ] FR-5: Health check verification.

## Log

- Replaced Spring Boot 4.2 and H2 with Spring Boot 3.3.13, PostgreSQL 16, Redis 7, and Flyway.
- Added local Docker Compose services, a production Dockerfile, initial Flyway schema, and PostgreSQL Testcontainers wiring.
- Renamed the source-of-truth document to `REQUIREMENTS.md` and aligned the Java base package to `com.example.shortener`.

## NEXT

Await the next explicitly named functional slice. Do not begin one automatically.

## Verify

```bash
docker compose up -d --wait && ./mvnw test
```
