# TinyLink

A URL shortener with custom aliases, link expiry, click analytics, per-IP rate limiting, Postgres storage and optional Redis caching. The backend is Spring Boot, the frontend is Angular, and the whole stack starts with one Docker Compose command.

## Features

- Create short links, with an optional custom alias and expiry date
- 302 redirects that record each click (time, user agent, referrer). Visitor IP addresses are not stored
- Public stats per link, and private analytics (clicks by referrer, hour and day, recent clicks) for the link owner
- An owner token is returned once when a link is created, and is needed to delete the link or read its analytics
- Links are validated: http or https only, no local or private addresses, no embedded credentials, alias rules
- Rate limiting per client IP (2 creates per minute and 10 per hour by default, plus a generous limit on redirects)
- Data is stored in Postgres, so links survive restarts
- Optional Redis caching of redirect lookups (turn off with `tinylink.cache.enabled=false`)
- Click history older than 90 days is removed automatically
- Health endpoint for container checks

## Tech stack

- Backend: Java 17, Spring Boot 4.1, Maven
- Frontend: Angular 22, served by nginx
- Postgres 16 for links and clicks (Spring Data JPA)
- Redis 7 for the optional redirect cache
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
| GET | `/api/analytics/{shortCode}` | Clicks by referrer, hour and day, plus recent clicks. Needs the `X-Owner-Token` header |
| DELETE | `/api/{shortCode}` | Delete a link and its click history. Needs the `X-Owner-Token` header |

Create a link:

```powershell
$body = '{"originalUrl":"https://example.com/","customAlias":"demo","expiresAt":"2026-12-01T15:30:00Z"}'
Invoke-RestMethod -Method Post -Uri http://localhost:4200/api/shorten -ContentType "application/json" -Body $body
```

`customAlias` and `expiresAt` are optional. `expiresAt` is an ISO 8601 instant with a zone, for example `2026-12-01T15:30:00Z`. Aliases are 3 to 32 letters, digits, hyphens or underscores. Without an alias, a random 6 character code is generated. A duplicate alias or an invalid URL returns 400, and going over the rate limit returns 429 with the number of requests remaining and the time until reset.

The response includes an `ownerToken`. It is shown only once and only its hash is stored, so keep it. Use it like this:

```powershell
Invoke-RestMethod -Method Get -Uri http://localhost:4200/api/analytics/demo -Headers @{ "X-Owner-Token" = "YOUR_TOKEN" }
Invoke-RestMethod -Method Delete -Uri http://localhost:4200/api/demo -Headers @{ "X-Owner-Token" = "YOUR_TOKEN" }
```

## Configuration

Settings live in `backend/src/main/resources/application.yaml`.

| Property | Default | Description |
| --- | --- | --- |
| `tinylink.base-url` | `http://localhost:8080` | Used to build the short links. Compose sets it to `http://localhost:4200` |
| `tinylink.short-code.length` | 6 | Length of generated codes |
| `tinylink.rate-limit.requests-per-minute` | 2 | Per client IP |
| `tinylink.rate-limit.requests-per-hour` | 10 | Per client IP |
| `tinylink.rate-limit.redirects-per-minute` | 120 | Redirects per client IP |
| `tinylink.cache.enabled` | true | Use Redis for redirect lookups. Set to false when there is no Redis |
| `tinylink.cache.ttl-minutes` | 30 | How long a redirect stays cached in Redis |
| `tinylink.analytics.retention-days` | 90 | How long click records are kept |
| `tinylink.stats-timezone` | `UTC` | Time zone used to group analytics by hour and day |
| `tinylink.client-ip.proxy-hops` | 0 | Number of trusted proxies in front of the app. 0 ignores `X-Forwarded-For` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | local Postgres | Database connection. Compose sets them |
| `spring.data.redis.host` | `localhost` | Compose sets it to `redis` |

Any property can be overridden with an environment variable. The compose file does this with `SPRING_DATA_REDIS_HOST` and `TINYLINK_BASE_URL`.

## Local development

Backend, with Postgres and Redis running first (compose publishes them on localhost only):

```powershell
docker compose up -d postgres redis
cd backend
.\mvnw.cmd spring-boot:run
```

Run the unit tests with `.\mvnw.cmd test`.

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
  docker-compose.yaml   Postgres, Redis, backend and frontend
  Dockerfile.cloudrun   Single image (Angular + API) for Cloud Run
  deploy/cloudrun/      Cloud Run deployment guide (DEPLOY.md)
```

In Docker, nginx serves the built Angular files and forwards `/api/` requests to the backend container. Only the frontend port is published, so everything goes through http://localhost:4200.

## Deploying

- Render (free, no card): [deploy/render/DEPLOY.md](deploy/render/DEPLOY.md)
- Google Cloud Run: [deploy/cloudrun/DEPLOY.md](deploy/cloudrun/DEPLOY.md) (needs a billing account)

## Health check

`GET /actuator/health/liveness` returns `{"status":"UP"}` when the app is running. The backend container uses it for its Docker healthcheck.

## Known limitations

- Rate limits are held in memory, so they reset on restart and are only correct with a single backend instance.
- Behind Docker Desktop, every local request can appear to come from the same address, so they share one rate limit.
- The schema is created automatically (`ddl-auto: update`). Move to Flyway migrations if the tables change often.
- Anyone with the short code can read the public stats. Only the owner token unlocks analytics and delete.
- Timestamps are stored in UTC. Hourly and daily analytics use `tinylink.stats-timezone`.

## Troubleshooting

- **Port 4200 is already in use:** stop `ng serve`, or change the published port for the frontend in `docker-compose.yaml`.
- **A port will not bind on Windows:** Windows can reserve port ranges. Check them with `netsh interface ipv4 show excludedportrange protocol=tcp` and pick a port outside the listed ranges.
- **502 Bad Gateway after rebuilding the backend:** run `docker compose restart frontend`. nginx looks up the backend address when it starts.
- **Backend shows unhealthy:** run `docker compose logs backend` and read the last lines.

## License

See the LICENSE file.
