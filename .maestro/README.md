# Maestro — Synthetic Monitor

This directory contains the Maestro end-to-end test suite for Ushrashuvchi.

## Running locally

```bash
maestro test .maestro/synthetic_critical_path.yaml
```

Requires a connected Android device or emulator with `adb` visible, and the
[Maestro CLI](https://maestro.mobile.dev/getting-started/installing-maestro) installed.

## Running on Maestro Cloud

```bash
maestro cloud --apiKey $MAESTRO_CLOUD_API_KEY .maestro/
```

Set `MAESTRO_CLOUD_API_KEY` in your environment or CI secrets before running.

## Production synthetic monitor

`synthetic_critical_path.yaml` is the **production synthetic monitor**. It
exercises the full critical path — record audio, trigger AI generation, assert
the Summary tab appears — to catch regressions in the Gemini pipeline or core
recording flow before users do.

It runs nightly via the **`load-and-maestro`** GitHub Actions job defined in
`.github/workflows/`. Any failure pages the on-call rotation.
