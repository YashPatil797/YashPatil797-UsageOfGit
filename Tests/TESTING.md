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
```

Expected: `401`, `403`, `200` with `"active":false`, then `410 Gone` with `"error":"inactive"`.

## 9. Automated checks

```bash
./mvnw -q compile
./mvnw -q test -Dtest=Base62Test
```

Run the container checks on a machine with Docker installed.
