# URL Shortener Service — Requirements

## 1. Objective

Build a runnable, cloud-deployable URL shortener service that converts a valid long URL into a unique short URL and redirects visitors from the short URL to the original destination.

The implementation should prioritize correctness, simplicity, and demonstrable deployment within a three-hour coding challenge.

## 2. Functional Requirements

### 2.1 Create a Short URL

- Accept a required destination URL and an optional custom alias.
- Validate that the destination is an absolute URL using the `http` or `https` scheme.
- When no alias is supplied, generate a unique, URL-safe short code.
- Detect generated-code collisions and retry without overwriting an existing link.
- Establish ownership when the link is created, either from an authenticated caller identity or by issuing an unguessable management credential.
- Persist the link and return:
  - The short code.
  - The complete short URL.
  - The original destination URL.
  - Its creation time and active status.

### 2.2 Custom Alias

- Allow a caller to request a user-defined short code such as `my-link`.
- An alias must:
  - Contain only letters, numbers, hyphens, and underscores.
  - Meet documented minimum and maximum length limits.
  - Not match a reserved application path such as `api` or `health`.
- Reject malformed or disallowed aliases with `400 Bad Request`.
- Reject an alias already assigned to any link, including an inactive link, with `409 Conflict`.
- Do not silently replace an invalid or duplicate requested alias with a generated code.
- Fall back to automatic code generation only when the alias is omitted.

### 2.3 Redirect a Short URL

- Look up the requested short code.
- Redirect an active link to its original URL.
- Use `302 Found` by default for a temporary redirect.
- Support `301 Moved Permanently` when the link is explicitly configured for a permanent redirect.
- Set the `Location` response header to the original URL.
- Increment the hit count exactly once for each successful redirect.
- Return `404 Not Found` with a meaningful error when the code does not exist.
- Return `410 Gone` with a meaningful error when the code exists but is inactive.
- Never redirect an unknown or inactive link.

### 2.4 Metadata and Link Status

- Return metadata for a given short code containing:
  - Short code and complete short URL.
  - Original URL.
  - Creation time in a documented UTC timestamp format.
  - Hit count.
  - Active/inactive status.
- A new link must be active and have a hit count of zero.
- Reading metadata must not increment the hit count.
- Allow only the link owner to deactivate a link without deleting its record.
- Deactivation must preserve the URL, metadata, hit count, and alias reservation.
- The service must authenticate the management request and verify link ownership. The chosen ownership mechanism may be lightweight, but it must be documented and must not expose credentials in URLs or logs.

### 2.5 Health Check

- Expose a health endpoint for deployment and uptime checks.
- Return a successful response when the application is running and able to serve requests.

## 3. API Contract

The exact path names may vary, but the service must provide equivalent behavior.

### Create

- Method: `POST`
- Path: `/api/urls`
- Request content type: `application/json`
- Request body:
  - `url`: required string containing the destination URL.
  - `alias`: optional custom short-code string.
  - `redirectType`: optional value of `temporary` or `permanent`; defaults to `temporary`.
- Success status: `201 Created`
- Invalid input status: `400 Bad Request`
- Duplicate alias status: `409 Conflict`
- Response format: JSON
- The response must provide the information needed by the creator to perform future authorized management operations; any management credential must be returned securely and must not appear in the short URL.

### Redirect

- Method: `GET`
- Path: `/{shortCode}`
- Temporary redirect status: `302 Found`
- Permanent redirect status: `301 Moved Permanently`
- Unknown code status: `404 Not Found`
- Inactive code status: `410 Gone`
- The response must set the `Location` header to the stored destination URL.

### Get Metadata

- Method: `GET`
- Path: `/api/urls/{shortCode}`
- Success status: `200 OK`
- Unknown code status: `404 Not Found`
- Response format: JSON containing the required metadata fields.

### Deactivate

- Method: `PATCH`
- Path: `/api/urls/{shortCode}`
- Request body: `{ "active": false }`
- Authentication: required
- Success status: `200 OK`
- Unauthenticated status: `401 Unauthorized`
- Non-owner status: `403 Forbidden`
- Unknown code status: `404 Not Found`
- The response must return the updated metadata.

### Health

- Method: `GET`
- Path: `/health`
- Success status: `200 OK`
- Response format: JSON or plain text

### Errors

- API errors should use a consistent JSON structure.
- Error responses should include a human-readable message.
- Internal implementation details and stack traces must not be exposed.

## 4. Data Requirements

Each stored URL record must contain:

- A unique short code.
- The original destination URL.
- A creation timestamp.
- A non-negative hit count.
- An active/inactive status.
- An owner identifier.
- The configured redirect type.

Persistence must survive application restarts in the deployed environment. A managed relational database is preferred for cloud deployment. An embedded database is acceptable for local development if its limitations are documented.

Short codes must:

- Be unique.
- Be safe to use in a URL path.
- Have sufficient randomness or key space to make collisions unlikely.
- Be regenerated or retried safely if a collision occurs.
- Remain reserved after deactivation.

Hit-count updates must be atomic so concurrent redirects do not lose increments.

## 5. Non-Functional Requirements

### Security

- Reject unsupported URL schemes such as `javascript:`, `data:`, and `file:`.
- Use parameterized database operations or an equivalent safe persistence API.
- Do not commit credentials or secrets.
- Read configuration and secrets from environment variables.
- Apply a reasonable request-body size limit.
- Document the risk of redirecting to arbitrary user-supplied URLs.
- Authenticate link-management requests and authorize deactivation against the stored owner.
- Do not expose ownership credentials or management tokens in public metadata.

### Reliability

- Handle malformed requests without crashing.
- Handle duplicate-code collisions safely.
- Log startup failures and unexpected server errors.
- Shut down cleanly when the hosting platform sends a termination signal.

### Performance

- Redirect lookup should use an indexed or key-based query.
- The service should respond without unnecessary external calls.
- No large-scale distributed design is required for the MVP.

### Portability

- The service must run locally using documented commands.
- It must listen on a configurable port and network interface suitable for cloud hosting.
- It must include all deployment configuration required by the selected cloud platform.

## 6. Configuration

Configuration should be supplied through environment variables, including:

- Application port.
- Public base URL used to construct short URLs.
- Database connection string or equivalent persistence configuration.
- Runtime environment or log level, if needed.

Required variables, defaults, and local-development values must be documented.

## 7. Testing and Acceptance Criteria

The solution is accepted when all of the following can be demonstrated:

1. A valid `http` or `https` URL can be shortened.
2. The creation response contains the unique code and complete usable short URL.
3. Omitting an alias generates a unique short code.
4. A valid available custom alias is used exactly as requested.
5. An invalid alias returns `400`, and a duplicate alias returns `409`.
6. An active temporary link redirects with `302` and the correct `Location` header.
7. A link configured as permanent redirects with `301` and the correct `Location` header.
8. An unknown short code returns a meaningful `404` error.
9. A successful redirect increments the hit count exactly once.
10. Metadata returns the original URL, creation time, hit count, and active status without changing the hit count.
11. The owner can deactivate a link, while an unauthenticated caller or non-owner cannot.
12. An inactive link retains its metadata, cannot be reused as an alias, and returns `410` instead of redirecting.
13. A missing, malformed, or unsupported URL returns `400`.
14. Stored mappings remain available after an application restart in the deployed environment.
15. The health endpoint returns `200`.
16. Automated tests cover creation, aliases, redirects, metadata, hit counting, ownership, deactivation, validation, and error cases.
17. The application can be deployed by following the repository documentation.

## 8. Documentation Deliverables

The project documentation must include:

- A short architecture overview.
- Technology choices and trade-offs.
- Local setup and run instructions.
- Test instructions.
- Required environment variables.
- Cloud deployment instructions.
- Example requests and responses.
- Known limitations.

## 9. Deployment Deliverables

- A publicly reachable deployment of the service.
- A reproducible deployment configuration.
- A health-check configuration compatible with the selected platform.
- A documented deployed base URL.
- No secrets stored in source control.

## 10. Out of Scope for the MVP

The following features are optional and should only be attempted after the core service is complete, tested, and deployed:

- Full user registration and account management beyond the minimum ownership mechanism.
- Link expiration.
- Link editing or deletion.
- Analytics beyond the required hit count.
- QR-code generation.
- Abuse detection or malicious-URL scanning.
- Admin dashboards.
- Bulk URL creation.
- Distributed caching.
- Multi-region deployment.
- High-availability or internet-scale architecture.

## 11. Optional Enhancements

If time remains:

- Add expiration timestamps.
- Add rate limiting to URL creation.
- Provide an API specification such as OpenAPI.
- Add a minimal browser interface.
