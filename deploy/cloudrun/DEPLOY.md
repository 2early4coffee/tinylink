# Deploying TinyLink to Cloud Run on the free tier

One container serves the Angular app and the API. The database is an external free Postgres.
Run all commands from the repository root in PowerShell.

## Keeping it free

- `--max-instances 1` means traffic can never fan out into more billed instances.
- `--min-instances 0` means nothing runs, and nothing is billed, while nobody is using it.
- Do not pass `--no-cpu-throttling` or `--min-instances` above 0, both cause billing while idle.
- Budget alerts only email you. Set one anyway (Billing, Budgets and alerts) at a very low amount.
- Free allowance per month, request-based billing: 2 million requests, 180,000 vCPU-seconds,
  360,000 GiB-seconds. With 1 vCPU and 768 MiB, the vCPU number is the one you would hit first
  (about 50 hours of active request time).
- Check the Artifact Registry page in the console now and then, and delete old image versions.

## One-time setup

1. Create a Google Cloud project, link a billing account, and install the gcloud CLI.
2. Create a free Postgres database (for example at neon.com) and note host, database name, user and password.

```powershell
gcloud auth login
gcloud config set project YOUR_PROJECT_ID
gcloud services enable run.googleapis.com artifactregistry.googleapis.com
gcloud artifacts repositories create tinylink --repository-format=docker --location=europe-west1
gcloud auth configure-docker europe-west1-docker.pkg.dev
```

3. Copy `deploy/cloudrun/env.example.yaml` to `deploy/cloudrun/env.yaml` and fill it in.

## Build, push, deploy

The image is built on your own machine with Docker Desktop, which avoids Cloud Build entirely.

```powershell
$PROJECT = "YOUR_PROJECT_ID"
$IMG = "europe-west1-docker.pkg.dev/$PROJECT/tinylink/app:1"

docker build --platform linux/amd64 -f Dockerfile.cloudrun -t $IMG .
docker push $IMG

gcloud run deploy tinylink `
  --image $IMG `
  --region europe-west1 `
  --allow-unauthenticated `
  --min-instances 0 `
  --max-instances 1 `
  --concurrency 40 `
  --cpu 1 `
  --memory 768Mi `
  --cpu-boost `
  --timeout 30 `
  --port 8080 `
  --env-vars-file deploy/cloudrun/env.yaml
```

The command prints the service URL. Put it in `TINYLINK_BASE_URL` in `env.yaml` and apply it:

```powershell
gcloud run services update tinylink --region europe-west1 --env-vars-file deploy/cloudrun/env.yaml
```

For later releases, bump the tag (`:2`, `:3`), build and push, then run the same `gcloud run deploy`.

## Check it

The first request after a quiet period is slow (the app boots and the database wakes up, 10 to 25 seconds).

1. Open the service URL and create a link.
2. Confirm the rate limiter sees real visitors. Run this three times quickly, with a different fake
   `X-Forwarded-For` each time. The third call must return 429 (the limit is 2 per minute), which shows
   a forged header does not give a fresh allowance.

```powershell
1..3 | ForEach-Object {
  curl.exe -s -o NUL -w "%{http_code}`n" -X POST https://YOUR-SERVICE-URL/api/shorten `
    -H "Content-Type: application/json" -H "X-Forwarded-For: 9.9.9.$_" `
    -d '{"originalUrl":"https://example.com/"}'
}
```

3. Try the same from a phone on mobile data. It should work, because it is a different visitor.
   If instead everyone is blocked together, change `TINYLINK_CLIENT_IP_PROXY_HOPS` to `2` and update the service.
   If a forged header gets through, change it to `1`.

## Taking it down

```powershell
gcloud run services delete tinylink --region europe-west1
```
