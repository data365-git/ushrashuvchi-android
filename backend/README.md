# Ushrashuvchi Backend

Ktor backend for the Ushrashuvchi Android app — handles cloud sync, share links, and public meeting viewer.

## Status: SCAFFOLD ONLY

This is the project skeleton. To complete:
1. Implement endpoints in each route file (see TODOs)
2. Run `railway init` from this directory
3. `railway add --plugin postgresql`
4. `railway volume add --service api --mount-path /data --size 50`
5. `railway variables set --service api JWT_SECRET="$(openssl rand -hex 32)"`
6. `railway up`

## Local dev
```bash
./gradlew run
```

## Endpoints (planned)
- POST /api/v1/devices/register → JWT
- POST /api/v1/meetings (auth)
- POST /api/v1/meetings/{id}/audio (auth, multipart)
- GET /api/v1/meetings/{id}/audio (auth, Range)
- POST /api/v1/meetings/{id}/share (auth) → {token, url}
- GET /api/v1/public/{token}
- GET /api/v1/public/{token}/audio (Range, rate-limited)
- POST /api/v1/public/{token}/ask
- GET /health
