# Security Policy

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

Email security disclosures to **data365.services@gmail.com** with the subject line `[SECURITY] ushrashuvchi-android`. We will acknowledge within 72 hours and aim to release a fix within 14 days for confirmed issues.

Please include:
- A description of the vulnerability
- Steps to reproduce
- Affected version (`versionCode` / `versionName` from `app/build.gradle.kts`)
- Any proof-of-concept code or screenshots (if safe to share)

## Out of Scope

The following are **not** considered security issues for this project:

- Issues that require physical, unlocked access to the user's device
- Issues requiring root or developer-mode access
- Denial-of-service via abnormally large audio files
- Theoretical attacks with no practical exploitation path

## Supported Versions

| Version | Supported |
|---------|-----------|
| Latest (`main`) | ✅ |
| Older releases | ❌ — please upgrade |

## Known Limitations

- The Gemini API key entered in Settings is stored in `SharedPreferences` (not in the Android Keystore). This is a known limitation — if a user's device is compromised at root level, the key is readable. A future version will migrate to the Keystore.
- Audio recordings are stored in app-scoped external storage (`/Android/data/…/Recordings/`). They are not accessible to other apps without explicit user sharing.
