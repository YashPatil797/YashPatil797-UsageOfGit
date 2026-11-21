# URL Shortener — High-Level Design

## 1. Purpose

This document translates the functional and non-functional requirements in `requirements.md` into an implementation architecture for the Spring Boot URL shortener.

The design favors a small, testable, cloud-deployable service that can be completed within the challenge scope. Distributed caching, multi-region deployment, and advanced analytics are intentionally excluded from the MVP.

## 2. Architecture

The application is a stateless Spring Boot service backed by a relational database.

```text
                       +----------------------+
                       |   Client / Browser   |
                       +----------+-----------+
                                  |
                              HTTPS / JSON
                                  |
                       +----------v-----------+
                       | Load Balancer /      |
                       | Cloud Ingress        |
                       +----------+-----------+
                                  |
                       +----------v-----------+
                       | Spring Boot Service  |
                       |                      |
                       | - REST controllers   |
                       | - Validation         |
                       | - URL service        |
                       | - Ownership checks   |
                       | - Error handling     |
                       +----------+-----------+
                                  |
                             JPA / JDBC
                                  |
                       +----------v-----------+
                       | PostgreSQL Database  |
                       +----------------------+
```

H2 may be used for local development and automated tests. PostgreSQL is the production persistence store.

## 3. Main Components

### 3.1 URL Management Controller

Handles management endpoints under `/api/urls`:

- Creates short links.
- Accepts and validates optional aliases.
- Returns link metadata.
- Deactivates links after authenticating the owner.

The controller performs request-shape validation and delegates business rules to the URL service.

### 3.2 Redirect Controller

Handles `GET /{shortCode}`:

- Finds the link by its unique code.
- Returns `404 Not Found` if it does not exist.
- Returns `410 Gone` if it is inactive.
- Atomically increments the hit count for an active link.
- Returns `302 Found` for temporary links or `301 Moved Permanently` for permanent links.
- Sets the original URL in the `Location` header.

### 3.3 URL Service

Contains the main business rules:

- Destination URL validation.
- Custom-alias validation and reserved-word checks.
- Automatic short-code generation and collision retry.
- Link creation and metadata mapping.
- Redirect-status selection.
- Ownership verification and deactivation.

### 3.4 Persistence Layer

A Spring Data JPA repository provides database access. The database is the source of truth for URL mappings, status, ownership, and hit counts.

The short-code column has a database-level unique constraint. This guarantees uniqueness under concurrent create requests even if application-level checks race.

### 3.5 Management Credential

The MVP uses a lightweight per-link management token:

1. A cryptographically secure random token is generated when a link is created.
2. The raw token is returned once in the creation response.
3. Only a one-way hash of the token is stored.
4. Deactivation requires `Authorization: Bearer <management-token>`.
5. The supplied token is hashed and compared with the stored value.

This avoids implementing full user accounts while still establishing ownership. Tokens must never appear in short URLs, logs, or public metadata.

### 3.6 Health Endpoint

`GET /health` reports application availability. A deeper readiness check may verify database connectivity for use by the cloud platform.

## 4. API Design

| Operation | Method and path | Success | Main errors |
|---|---|---:|---|
| Create link | `POST /api/urls` | `201` | `400`, `409` |
| Redirect | `GET /{shortCode}` | `301` or `302` | `404`, `410` |
| Get metadata | `GET /api/urls/{shortCode}` | `200` | `404` |
| Deactivate | `PATCH /api/urls/{shortCode}` | `200` | `400`, `401`, `403`, `404` |
| Health | `GET /health` | `200` | `503` |

### 4.1 Create Request

```json
{
  "url": "https://example.com/articles/123",
  "alias": "my-link",
  "redirectType": "temporary"
}
```

`alias` and `redirectType` are optional. `redirectType` defaults to `temporary`.

The response contains the short code, complete short URL, original URL, creation time, active status, hit count, redirect type, and one-time management token.

### 4.2 Metadata Response

```json
{
  "shortCode": "my-link",
  "shortUrl": "https://sho.rt/my-link",
  "originalUrl": "https://example.com/articles/123",
  "createdAt": "2026-11-21T05:30:00Z",
  "hitCount": 4,
  "active": true,
  "redirectType": "temporary"
}
```

The management token and its hash are never returned by the metadata endpoint.

### 4.3 Error Response

All API errors use a consistent shape:

```json
{
  "code": "ALIAS_ALREADY_EXISTS",
  "message": "The requested alias is already in use.",
  "timestamp": "2026-11-21T05:30:00Z"
}
```

Stack traces and internal exception details are not returned.

## 5. Data Model

### `short_url`

| Column | Type | Constraints / purpose |
|---|---|---|
| `id` | UUID or BIGINT | Primary key |
| `short_code` | VARCHAR(50) | Unique, indexed, not null |
| `original_url` | TEXT | Not null |
| `created_at` | TIMESTAMP WITH TIME ZONE | UTC, not null |
| `hit_count` | BIGINT | Not null, default `0`, non-negative |
| `active` | BOOLEAN | Not null, default `true` |
| `redirect_type` | VARCHAR(16) | `TEMPORARY` or `PERMANENT` |
| `owner_id` | UUID | Internal link-owner identifier |
| `management_token_hash` | VARCHAR | Not null; never returned |

Inactive records are retained, and their short codes remain reserved.

## 6. Key Request Flows

### 6.1 Automatic Code Creation

1. Validate the destination URL and redirect type.
2. Generate a URL-safe random code using a cryptographically secure generator and a Base62-style alphabet.
3. Generate the owner identifier and management token.
4. Insert the record with `active = true` and `hit_count = 0`.
5. If the unique constraint reports a code collision, generate another code and retry a limited number of times.
6. Return `201 Created` with the full short URL and raw management token.

### 6.2 Custom Alias Creation

1. Validate the URL.
2. Validate the alias against the documented length, allowed characters, and reserved names.
3. Attempt to insert the alias.
4. Translate a unique-constraint violation into `409 Conflict`.
5. Never replace an invalid or duplicate requested alias with an automatic code.

A recommended alias rule is `^[A-Za-z0-9_-]{3,50}$`. Matching of aliases should be consistently case-sensitive or case-insensitive; the MVP will use case-sensitive matching and document that decision.

### 6.3 Redirect

1. Query by the indexed short code.
2. Return `404` when no record exists.
3. Return `410` when the record is inactive.
4. Atomically execute an update equivalent to `hit_count = hit_count + 1`.
5. Return the configured redirect status with the original URL in `Location`.

The count update occurs only for active links. Concurrent requests cannot overwrite one another's increments.

### 6.4 Deactivation

1. Require a bearer management token.
2. Find the record or return `404`.
3. Verify the token hash using a timing-safe comparison.
4. Return `401` when no valid credential is supplied and `403` when the authenticated credential does not own the link.
5. Set `active = false` without deleting or resetting the record.
6. Return the updated metadata.

## 7. Correctness and Concurrency

- The database unique constraint is the final authority for code uniqueness.
- Automatic collision retries are bounded; exhaustion returns a server error and is logged.
- Hit count uses an atomic database update to prevent lost increments.
- Deactivation is transactional and idempotent: deactivating an already inactive owned link returns its inactive state.
- Metadata reads never modify hit count.
- UTC is used for persisted and returned timestamps.

Strict end-to-end “exactly once” counting cannot be guaranteed if a connection fails after the database update but before the caller receives the redirect. The MVP defines a hit as a redirect response produced by the server and provides atomic, no-lost-update counting under normal operation.

## 8. Security

- Accept only absolute `http` and `https` destination URLs.
- Reject `javascript:`, `data:`, `file:`, and malformed URLs.
- Do not fetch destination URLs, which avoids server-side request forgery during creation.
- Use bean validation and a request-body size limit.
- Store only hashed management tokens.
- Keep database credentials and application secrets in environment variables.
- Use HTTPS at the cloud ingress.
- Avoid logging destination query strings, authorization headers, or management tokens.
- Apply rate limiting to creation and management endpoints if time permits.
- Document that arbitrary redirects can be abused for phishing; malicious-URL scanning is outside the MVP.

## 9. Redirect Status Trade-off

`302 Found` is the safe default because clients continue to contact the service, allowing hit counting and deactivation to remain effective.

`301 Moved Permanently` may be cached by browsers and intermediaries. A cached client can bypass future hit counting or deactivation. The API supports `301` because it is required, but callers must explicitly request a permanent redirect.

## 10. Configuration

The service reads configuration from environment variables:

| Variable | Purpose |
|---|---|
| `PORT` | HTTP port assigned by the platform |
| `PUBLIC_BASE_URL` | Base used to construct complete short URLs |
| `DATABASE_URL` | Production database connection |
| `DATABASE_USERNAME` | Database user when not embedded in the URL |
| `DATABASE_PASSWORD` | Database password |
| `LOG_LEVEL` | Optional runtime logging level |

Local development may use an H2 profile. Production uses PostgreSQL and schema migrations.

## 11. Deployment

The deployable unit is an executable Spring Boot JAR or container image.

Production topology:

- One or more stateless application instances.
- A managed PostgreSQL database.
- A cloud load balancer or ingress terminating HTTPS.
- Platform health checks calling `/health`.
- Environment-based configuration and secret injection.

Graceful shutdown allows in-flight requests to complete when an instance is replaced.

## 12. Scaling Path

The MVP requires no distributed cache. Indexed database lookup is sufficient for the challenge workload.

If traffic grows:

1. Scale stateless application instances horizontally.
2. Add database connection pooling and read monitoring.
3. Cache active URL mappings, with explicit invalidation on deactivation.
4. Move hit events to an asynchronous durable queue if exact synchronous counters become a bottleneck.
5. Partition data by a stable hash of the short code only when a single database is no longer sufficient.

Caching and asynchronous counting add consistency trade-offs and are deliberately deferred.

## 13. Testing Strategy

- Unit tests for URL, alias, reserved-name, redirect-type, and token validation.
- Service tests for code generation, collision retry, ownership, and status transitions.
- Repository tests for uniqueness and atomic concurrent hit increments.
- Controller tests for all required HTTP statuses and response headers.
- Integration tests using a real PostgreSQL-compatible environment where practical.
- Smoke test for startup and `/health`.

## 14. Known Limitations

- No full user-account system.
- Public metadata reveals the original URL; protect the endpoint in a future private-link model.
- A client may cache `301` and bypass later deactivation or hit counting.
- No expiration, deletion, malicious-link scanning, advanced analytics, or multi-region guarantees.
