# Deploying TinyLink to Render (free)

One Docker service serves the Angular app and the API. The database is an external free Postgres.

## What to expect on the free plan

- The service sleeps after 15 minutes without traffic. Waking takes a while because a Spring Boot app
  starts on a very small CPU share. Expect one to several minutes for the first visitor.
- Free instances have 512 MB of RAM. The image is tuned for that (`Dockerfile.render`).
- Render gives each workspace 750 free instance hours a month, shared by ALL free services in it.
  One always-on service uses about 720 to 744 hours. If you have other free services running in the same
  workspace, do not keep TinyLink awake around the clock, or all of them can be suspended at month end.
  See "Keeping it awake" below for a schedule that fits.
- Render's own free Postgres expires after 30 days, so use an external database (below).

## 1. Database

Create a free Postgres at neon.com (or another provider). At signup, check whether a card is asked for
and whether a Europe region is offered. You need:

- the host, database name, user and password
- `DB_URL` in this form: `jdbc:postgresql://HOST/DBNAME?sslmode=require`
  (use the direct host, not the one with `-pooler` in the name)

## 2. Create the service

1. Push the repository to GitHub (the files `render.yaml` and `Dockerfile.render` must be on the branch you deploy).
2. In the Render dashboard choose New, then Blueprint, and select the `tinylink` repository and branch.
3. Render reads `render.yaml` and asks for the four values marked as secret:
   `DB_URL`, `DB_USER`, `DB_PASSWORD` and `TINYLINK_BASE_URL`.
   For `TINYLINK_BASE_URL` enter a placeholder first (for example `https://tinylink.onrender.com`).
4. Create the service and wait for the first build. It takes several minutes.
5. Copy the real service URL from the top of the page. In the service's Environment tab, set
   `TINYLINK_BASE_URL` to it (no trailing slash). Render restarts the service.

If you prefer not to use a Blueprint, create a Web Service by hand: Runtime Docker, Dockerfile path
`./Dockerfile.render`, Docker build context `.`, plan Free, health check path `/actuator/health/liveness`,
and add the same environment variables as in `render.yaml`.

## 3. Check it

Open the service URL (be patient the first time) and create a link. Then check the rate limiter sees real
visitors. Run this three times quickly, with a different fake `X-Forwarded-For` each time. The third call
must return 429 (the limit is 2 creates a minute), which shows a forged header does not help.

```powershell
1..3 | ForEach-Object {
  curl.exe -s -o NUL -w "%{http_code}`n" -X POST https://YOUR-SERVICE.onrender.com/api/shorten `
    -H "Content-Type: application/json" -H "X-Forwarded-For: 9.9.9.$_" `
    -d '{\"originalUrl\":\"https://example.com/\"}'
}
```

Note: PowerShell 7 can mangle the quotes in the `-d` value. If you see a malformed-body error, use
`Invoke-RestMethod` as in the README instead.

Then open the site from a phone on mobile data and create a link. If that is blocked while your laptop
just used the limit, everyone shares one bucket and `TINYLINK_CLIENT_IP_PROXY_HOPS` is wrong. Try `2`.

## 4. Make wake-ups faster (optional)

The first deploy needs `SPRING_JPA_HIBERNATE_DDL_AUTO=update` (the default) to create the tables.
After the first successful start, add `SPRING_JPA_HIBERNATE_DDL_AUTO=none` in the Environment tab.
The app then skips checking the schema on every start, which saves time on a slow CPU.

## 5. Keeping it awake (optional)

A monitor that calls `https://YOUR-SERVICE.onrender.com/actuator/health/liveness` every 10 minutes stops
the 15 minute sleep, so visitors skip the long wake-up. Two things limit how you should do it:

- **Render hours.** Awake around the clock costs about 720 to 744 of the 750 free hours, shared by every
  free service in the workspace. Ping only during the hours people use the site. For example, 07:00 to
  23:00 Nairobi time is 16 hours a day, about 490 a month, which leaves room for another service.
  A tool that cannot schedule hours (as far as I know that includes UptimeRobot's free plan) will keep it
  awake all day. cron-job.org lets you pick the hours and sets any interval from one minute.
- **Neon hours.** Neon's free plan includes 100 compute hours a month and suspends the database after
  5 minutes idle. Waking it takes a fraction of a second, so there is nothing to gain from keeping it awake,
  and a database that never sleeps would use roughly 180 hours and be suspended until the next cycle.
  The liveness endpoint does not query the database and the connection pool closes idle connections after
  30 seconds, so these pings leave Neon free to sleep. Never ping an endpoint that reads the database.

Use the GET method and an interval below 15 minutes (10 is a good choice).

## Troubleshooting

- **Deploy fails with "no open port detected":** startup is slow on the free CPU. Look at the logs, and
  redeploy once. If it keeps timing out, tell me what the log shows.
- **Out of memory (killed):** change `-Xmx256m` in `Dockerfile.render` to `-Xmx200m` and redeploy.
- **503 or very slow right after idle:** the service is waking up. Wait and refresh.
- **Database errors on first request:** the free database may be waking up. Retry after a few seconds.
