# Agent instructions: URL Shortener (3-hour timed build)

## Stack

Java 21, Spring Boot 3.3.x, Maven, PostgreSQL 16 (source of truth), Redis 7 (cache),
Flyway, Docker, JUnit 5 + Testcontainers. Deploy: DigitalOcean App Platform.

## Conventions

- Package `com.example.shortener.{config,domain,repository,service,api,cache}`
- Java records for DTOs. No Lombok. Constructor injection only.
- Schema changes only via Flyway migrations (`ddl-auto=validate`).
- Config via environment variables: `DATABASE_URL`, `REDIS_URL`, `BASE_URL`, `PORT`.

## Working rules (important)

- `REQUIREMENTS.md` is the source of truth. Reference FR/NFR IDs.
- Implement ONLY the slice I name. Touch ONLY the files I list.
- Never start the next slice on your own.
- After each slice: update `PROGRESS.md` (tick box, log entry, mark NEXT).
- Every slice ends runnable and includes an exact verify command.
- Prefer simple over clever; we are time-boxed.
