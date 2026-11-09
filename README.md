# TinyLink

A URL shortener with custom aliases, link expiry, click analytics, per-IP rate limiting and Redis caching. The backend is Spring Boot, the frontend is Angular, and the whole stack starts with one Docker Compose command.

## Features

- Create short links, with an optional custom alias and expiry date
- 302 redirects that record each click (time, IP, user agent, referrer)
- Stats and analytics per link: clicks by referrer, hour and day, plus the most recent clicks
- Rate limiting per client IP (2 requests per minute, 10 per hour by default)
- Redis caching of redirect lookups, with an in-memory fallback if Redis is unreachable
- Scheduled cleanup of expired links
- Health endpoint for container checks

## Tech stack

- Backend: Java 17, Spring Boot 4.1, Maven
- Frontend: Angular 22, served by nginx
- Redis 7 for the cache and rate-limit counters
- Docker and Docker Compose

## Quick start (Docker)

You need Docker Desktop running.

```powershell
git clone https://github.com/2early4coffee/tinylink.git
cd tinylink
docker compose up --build
```

Open http://localhost:4200. The first build takes several minutes because it downloads base images and dependencies. Later builds are much faster.

Stop everything with `docker compose down`.

## API

All routes are under `/api`. Through Docker they are reachable at http://localhost:4200/api.

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/shorten` | Create a short link |
| GET | `/api/{shortCode}` | Redirect to the original URL (302) |
| GET | `/api/stats/{shortCode}` | Click count, status and dates |
| GET | `/api/analytics/{shortCode}` | Clicks by referrer, hour and day, plus recent clicks |
| DELETE | `/api/{shortCode}` | Deactivate a link |

Create a link:

```powershell
$body = '{"originalUrl":"https://example.com/","customAlias":"demo","expiresAt":"2026-12-01T15:30:00"}'
Invoke-RestMethod -Method Post -Uri http://localhost:4200/api/shorten -ContentType "application/json" -Body $body
```

`customAlias` and `expiresAt` are optional. Without an alias, a random 6 character code is generated. A duplicate alias returns 400, and going over the rate limit returns 429 with the number of requests remaining and the time until reset.

## Configuration

Settings live in `backend/src/main/resources/application.yaml`.

| Property | Default | Description |
| --- | --- | --- |
| `tinylink.base-url` | `http://localhost:8080` | Used to build the short links. Compose sets it to `http://localhost:4200` |
| `tinylink.short-code.length` | 6 | Length of generated codes |
| `tinylink.rate-limit.requests-per-minute` | 2 | Per client IP |
| `tinylink.rate-limit.requests-per-hour` | 10 | Per client IP |
| `tinylink.cache.ttl-minutes` | 30 | How long a redirect stays cached in Redis |
| `tinylink.cleanup.interval-minutes` | 1 | How often expired links are cleaned up |
| `spring.data.redis.host` | `localhost` | Compose sets it to `redis` |

Any property can be overridden with an environment variable. The compose file does this with `SPRING_DATA_REDIS_HOST` and `TINYLINK_BASE_URL`.

## Local development

Backend, with Redis running first:

```powershell
docker run -d --name redis -p 6379:6379 redis:7-alpine
cd backend
.\mvnw.cmd spring-boot:run
```

If the `redis` container already exists, start it with `docker start redis` instead.

Frontend, in a second terminal:

```powershell
cd frontend
npm install
npm start
```

The dev server runs on http://localhost:4200 and proxies `/api` to `localhost:8080` through `proxy.conf.json`. It needs Node 22.22.3 or newer. Stop the Docker stack first, because both use port 4200.

## Project structure

```
tinylink/
  backend/              Spring Boot API (Maven, Dockerfile)
  frontend/             Angular app (Dockerfile, nginx.conf, proxy.conf.json)
  docker-compose.yml    Redis, backend and frontend
```

In Docker, nginx serves the built Angular files and forwards `/api/` requests to the backend container. Only the frontend port is published, so everything goes through http://localhost:4200.

## Health check

`GET /actuator/health/liveness` returns `{"status":"UP"}` when the app is running. The backend container uses it for its Docker healthcheck.

## Known limitations

- Links and click counts are held in memory, so a backend restart clears them. Redis only holds the redirect cache and the rate-limit counters.
- Deleting a link deactivates it. The alias stays reserved.
- The container runs in UTC, so timestamps, expiry checks and the hourly stats use UTC.
- Rate limiting is per client IP. Behind Docker Desktop, every local request can appear to come from the same address.

## Troubleshooting

- **Port 4200 is already in use:** stop `ng serve`, or change the published port for the frontend in `docker-compose.yml`.
- **A port will not bind on Windows:** Windows can reserve port ranges. Check them with `netsh interface ipv4 show excludedportrange protocol=tcp` and pick a port outside the listed ranges.
- **502 Bad Gateway after rebuilding the backend:** run `docker compose restart frontend`. nginx looks up the backend address when it starts.
- **Backend shows unhealthy:** run `docker compose logs backend` and read the last lines.

## License

See the LICENSE file.
