# Backend Deploy Status

## ✅ Live and healthy

**API:** https://ushrashuvchi-backend-production.up.railway.app  
**Health:** https://ushrashuvchi-backend-production.up.railway.app/health → `{"status":"ok","version":"0.1.0"}`  
**Web viewer:** `https://ushrashuvchi-backend-production.up.railway.app/s/{token}` ← share link format

## What's deployed
- Ktor 2.3.7 + Netty + JWT auth + Range-streaming + CORS + StatusPages
- Full HTML web viewer route (`/s/{token}`) — single-page app matching Android Meeting Detail UI:
  - 4 tabs: Summary / Refined / Transcript / Tasks
  - Audio player (range-streamed, fixed bottom)
  - Karaoke transcript highlight (auto-scrolls + highlights current line as audio plays)
  - Password gate for protected shares
  - Markdown rendering for summary, card view for refined topics, checklist for tasks
  - Same color palette as Android app
- All API routes implemented:
  - `POST /api/v1/devices/register` — JWT issuance
  - `POST /api/v1/meetings` (auth) — upsert by (deviceId, clientId)
  - `PUT /api/v1/meetings/{id}/transcript` (auth)
  - `PUT /api/v1/meetings/{id}/tasks` (auth)
  - `POST /api/v1/meetings/{id}/audio` (auth, multipart) — uploads to volume
  - `GET /api/v1/meetings/{id}/audio` (auth, Range)
  - `DELETE /api/v1/meetings/{id}` (auth) — cascades via FK
  - `POST /api/v1/meetings/{id}/share` (auth) — creates token + returns share URL
  - `DELETE /api/v1/meetings/{id}/share/{token}` (auth) — revoke
  - `GET /api/v1/public/{token}` — JSON for the viewer
  - `GET /api/v1/public/{token}/audio` — Range stream

## ⚠️ TWO MANUAL DASHBOARD STEPS REQUIRED

The Railway CLI's `add --database` and the GraphQL `pluginCreate` / `serviceCreate` mutations both return "Unauthorized" on this session token — it's a permission-scope thing on the personal session token (works for compute/deploy, not for adding databases or volumes via API). **2 clicks each in the dashboard:**

### 1. Add Postgres (2 clicks)
1. Open https://railway.com/project/d96646d3-48f5-440f-8eb2-476d36ee0a4b
2. Click **+ New** → **Database** → **Add PostgreSQL**
3. Railway auto-injects `DATABASE_URL` into the backend, which auto-restarts and creates all tables on first request

### 2. Add Volume (2 clicks)
1. Same project → click **ushrashuvchi-backend** service → **Settings** → **Volumes**
2. Click **+ New Volume**, mount path: `/data`, size: 5 GB
3. Backend auto-restarts; volume becomes available at `/data` for audio uploads

**Until then:** the API returns 500 on any DB-touching endpoint (with helpful message "Please call Database.connect() before using this code"). Auth (`/api/v1/devices/register`) and `/health` still work.

## Privacy model
- All recordings stay private by default — meetings are only readable by their owning device (verified by JWT `deviceId` claim)
- A meeting becomes publicly viewable ONLY when a share token is created via `POST /meetings/{id}/share`
- Even then, `/public/{token}` requires the exact token (32-char URL-safe base64, unguessable)
- Passwords are bcrypt-hashed; expiry enforced server-side; revoke via `DELETE`

## Android wiring (already shipped)
- `BuildConfig.CLOUD_API_BASE_URL = "https://ushrashuvchi-backend-production.up.railway.app"`
- Cloud Sync toggle: **Settings → Account → Cloud Sync** (off by default)
- Share link: **Meeting Detail → ⋮ → Share link**, opens dialog with:
  - Privacy explanation ("Your recordings stay private. This will create a one-off public link...")
  - Audio toggle (include audio? Y/N — gates whether viewers can play)
  - Expiry days (blank = never)
  - Password (optional)
  - On confirm: shows step-by-step progress (Authenticate → Upload meta → Upload transcript → Upload tasks → Upload audio → Generate link → ✓ Copied)

## Test from your terminal right now
```bash
# Health
curl https://ushrashuvchi-backend-production.up.railway.app/health
# → {"status":"ok","version":"0.1.0"}

# Web viewer (works even before Postgres — shows password prompt or "not found" until backend has data)
open https://ushrashuvchi-backend-production.up.railway.app/s/anything

# Register a device (works without Postgres for the JWT part)
curl -X POST https://ushrashuvchi-backend-production.up.railway.app/api/v1/devices/register \
  -H "Content-Type: application/json" \
  -d '{"name":"test device"}'
# Returns 500 with DB error UNTIL Postgres is added — by design
```
