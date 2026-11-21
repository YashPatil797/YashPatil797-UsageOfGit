# URL Shortener Testing Guide

## 1. Start a clean environment

This removes existing local database data, which is useful after changing the initial Flyway migration:

```bash
docker compose down -v
docker compose up --build -d
docker compose ps
```

Check startup:

```bash
docker compose logs app-1
docker compose logs app-2
```

## 2. Health checks

```bash
curl -s localhost:8081/health
curl -s localhost:8082/health
```

Both instances should return `200 OK` with `"status":"UP"`. Health reflects PostgreSQL only, so it stays `UP` while Redis is stopped in the Redis fallback check below.

## 3. Create and redirect

```bash
RESPONSE=$(curl -s -X POST localhost:8081/api/v1/urls \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com"}')

echo "$RESPONSE"

CODE=$(echo "$RESPONSE" | sed -E 's/.*"code":"([^"]+)".*/\1/')
curl -i "localhost:8082/$CODE"
```

Expected: `302 Found` with `Location: https://example.com`.

## 4. Validation

Invalid URL:

```bash
curl -i -X POST localhost:8081/api/v1/urls \
  -H 'Content-Type: application/json' \
  -d '{"url":"javascript:alert(1)"}'
```

Expected: `400 Bad Request`.

Unknown code:

```bash
curl -i localhost:8081/nope123
```

Expected: `404 Not Found`.

## 5. Custom alias

```bash
curl -i -X POST localhost:8081/api/v1/urls \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com","alias":"my-sale"}'

curl -i localhost:8082/my-sale
```

Repeat the creation request with the same alias. Expected: `409 Conflict`.

## 6. Redis fallback

```bash
curl -s -o /dev/null localhost:8081/my-sale
docker compose exec redis redis-cli GET url:v2:my-sale
docker compose exec redis redis-cli TTL url:v2:my-sale

docker compose stop redis
curl -i localhost:8081/my-sale
docker compose start redis
```

The redirect should still return `302 Found` while Redis is unavailable.

## 7. Expired link

```bash
docker compose exec postgres psql -U shortener -d shortener \
  -c "UPDATE urls SET expires_at=now()-interval '1 minute' WHERE code='my-sale';"

docker compose exec redis redis-cli DEL url:v2:my-sale
curl -i localhost:8081/my-sale
```

Expected: `410 Gone`.

## 8. Metadata, permanent redirect, and deactivation

```bash
RESPONSE=$(curl -s -X POST localhost:8081/api/v1/urls \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/moved","alias":"moved-page","redirectType":"permanent"}')
TOKEN=$(echo "$RESPONSE" | sed -E 's/.*"managementToken":"([^"]+)".*/\1/')

curl -i localhost:8082/moved-page
curl -s localhost:8081/api/v1/urls/moved-page
```

Expected: `301 Moved Permanently`, then metadata with `"hitCount":1` and `"redirectType":"permanent"` and no token.

```bash
curl -i -X PATCH localhost:8081/api/v1/urls/moved-page -H 'Content-Type: application/json' -d '{"active":false}'
curl -i -X PATCH localhost:8081/api/v1/urls/moved-page -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer wrong' -d '{"active":false}'
curl -i -X PATCH localhost:8081/api/v1/urls/moved-page -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" -d '{"active":false}'
curl -i localhost:8082/moved-page
docker compose exec redis redis-cli EXISTS url:v2:moved-page
```

Expected: `401`, `403`, `200` with `"active":false`, then `410 Gone` with `"error":"inactive"`, and `0` from Redis because deactivation evicts the cached entry.

## 9. Link management edge cases

Run these after section 8, in the same shell so `$TOKEN` is still set:

```bash
# Repeating the deactivation is harmless
curl -s -o /dev/null -w '%{http_code}\n' -X PATCH localhost:8081/api/v1/urls/moved-page \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" -d '{"active":false}'
# Reactivation is not supported
curl -s -o /dev/null -w '%{http_code}\n' -X PATCH localhost:8081/api/v1/urls/moved-page \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" -d '{"active":true}'
# A deactivated alias stays reserved
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8081/api/v1/urls \
  -H 'Content-Type: application/json' -d '{"url":"https://example.com","alias":"moved-page"}'
# Invalid redirect type
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8081/api/v1/urls \
  -H 'Content-Type: application/json' -d '{"url":"https://example.com","redirectType":"forever"}'
# Unknown code
curl -s -o /dev/null -w '%{http_code}\n' localhost:8081/api/v1/urls/nope-nope
# Links created without redirectType keep using 302
CODE=$(curl -s -X POST localhost:8081/api/v1/urls -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/temp"}' | sed -E 's/.*"code":"([^"]+)".*/\1/')
curl -s -o /dev/null -w '%{http_code}\n' "localhost:8082/$CODE"
```

Expected, in order: `200`, `400`, `409`, `400`, `404`, `302`.

## 10. Tokens and errors in logs

Run this only while `$TOKEN` is set; with an empty pattern `grep` matches every line.

```bash
[ ${#TOKEN} -eq 43 ] && docker compose logs app-1 app-2 | grep -c -F "$TOKEN"
docker compose logs app-1 app-2 | grep -c -E 'ERROR|Unexpected request failure'
```

Expected: `0` and `0`.

## 11. Upgrade an existing database

This checks that the V2 migration runs on data created by the version before link management (`f91520d`). It does not remove volumes, so run it on a stack whose data you can keep or discard.

```bash
git checkout f91520d
docker compose up --build -d
until curl -sf localhost:8081/health >/dev/null && curl -sf localhost:8082/health >/dev/null; do sleep 2; done
curl -s -X POST localhost:8081/api/v1/urls -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/old","alias":"old-link"}'
curl -s -o /dev/null localhost:8082/old-link

git checkout Patil-Main
docker compose up --build -d
until curl -sf localhost:8081/health >/dev/null && curl -sf localhost:8082/health >/dev/null; do sleep 2; done
docker compose exec postgres psql -U shortener -d shortener \
  -c "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank;"
curl -i localhost:8082/old-link
curl -s localhost:8081/api/v1/urls/old-link
curl -i -X PATCH localhost:8081/api/v1/urls/old-link -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer anything' -d '{"active":false}'
```

Expected:

- Flyway shows versions `1` and `2`, both successful.
- `old-link` still returns `302` to `https://example.com/old`.
- Its metadata keeps the hit count from before the upgrade and shows `"redirectType":"temporary"`.
- The `PATCH` returns `403`, because links created before V2 have no management token.

## 12. Automated checks

```bash
./mvnw -q compile
./mvnw -q test -Dtest=Base62Test
./mvnw test
```

The full run needs Docker, because the integration tests start PostgreSQL and Redis with Testcontainers. Expected: `Tests run: 78, Failures: 0, Errors: 0, Skipped: 0`.
