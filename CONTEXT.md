# URL Shortener Project Context

Project: URL Shortener, a 3-hour timed challenge.
The service must run as multiple instances and deploy to DigitalOcean App Platform.

## Stack
- Java 21, Spring Boot 3.3.x, and Maven.
- PostgreSQL 16 is the source of truth; Redis 7 is a rebuildable cache.
- Flyway, Docker, JUnit 5, and Testcontainers.

## Conventions
- Packages: `com.example.shortener.{config,domain,repository,service,api,cache}`.
- Use Java records for DTOs; do not use Lombok.
- Use constructor injection only.
- Make schema changes only through Flyway migrations; use `ddl-auto=validate`.
- Configure with `DATABASE_URL`, `REDIS_URL`, `BASE_URL`, and `PORT`.

## Working Rules
- `REQUIREMENTS.md` is the source of truth; reference FR/NFR IDs.
- Implement only the named slice and touch only the files explicitly listed.
- Never begin the next slice automatically.
- After each slice, update `PROGRESS.md`: tick its box, add a log entry, and mark `NEXT`.
- End every slice in a runnable state and provide an exact verification command.
- Prefer simple, time-boxed solutions over clever abstractions.
