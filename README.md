# Ushrashuvchi — AI Meeting Recorder

**Ushrashuvchi** (Uzbek for "Meeting") is an Android app that records meetings and uses the Gemini API to generate summaries, chapter breakdowns, searchable transcripts, and task checklists — all from your voice.

> Works offline-first. Your audio never leaves your device except when you explicitly trigger AI analysis.

---

## Features

- **One-tap recording** — foreground service keeps recording even when the screen is off
- **AI-powered analysis** — send audio to Gemini 2.0/2.5 Flash; get back a structured summary, speaker-attributed transcript, chapters, and tasks
- **Folder library** — organise recordings into folders (sub-folders coming in Batch B)
- **Cross-meeting task list** — view and manage action items from all recordings in one place
- **Ask AI** — chat with the Gemini model about any recording
- **Multi-language UI** — English, Russian (Русский), and Uzbek (O'zbek)
- **Dark / light theme**
- **Export** — share the transcript as `.txt`, summary as `.md`, or tasks as `.csv`

---

## Screenshots

> _Screenshots coming soon — run the app and record a meeting to see it in action._

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM — `AppViewModel` (AndroidViewModel) + Repository |
| Database | Room (SQLite) via KSP-generated DAOs |
| Networking | Retrofit 2 + OkHttp + Moshi |
| AI / STT | Google Gemini API (`gemini-2.0-flash` / `gemini-2.5-flash`) |
| Audio | `MediaRecorder` (AAC/M4A, 64 kbps) inside a foreground service |
| Build | Gradle KTS, Secrets Gradle Plugin |
| Platform | Android minSdk 24, targetSdk 36 |

---

## Building

### Prerequisites

- Android Studio Meerkat (or later)
- Android SDK 36
- A [Google Gemini API key](https://aistudio.google.com/app/apikey) (free tier works)

### Steps

```bash
# 1. Clone
git clone https://github.com/data365-git/ushrashuvchi-android.git
cd ushrashuvchi-android

# 2. Set your Gemini key
cp .env.example .env
# Edit .env and replace MY_GEMINI_API_KEY with your real key

# 3. Open in Android Studio → Run on a device or emulator
#    (Or build from the terminal:)
./gradlew :app:installDebug
```

> **Note:** The debug build uses `debug.keystore` (password: `android`, alias: `androiddebugkey`).  
> Do **not** commit your own `.env` or release keystore — they are in `.gitignore`.

You can also set the API key at runtime: open the app → **Settings** → paste your key in the Gemini API Key field.

---

## Project Structure

```
app/src/main/java/com/example/
├── MainActivity.kt              # Single-activity entry point, NavHost
├── data/
│   ├── api/GeminiClient.kt      # Retrofit service + getAiResponse()
│   ├── dao/                     # Room DAOs (Meeting, Folder, RecordingSession, …)
│   ├── database/AppDatabase.kt  # Room database singleton (v5)
│   ├── localization/AppStrings.kt  # All UI strings in EN / RU / UZ
│   ├── model/Entities.kt        # Room entities
│   └── repository/MeetingRepository.kt  # Business logic + Gemini pipeline
├── audio/
│   ├── RecordingService.kt      # Foreground service (pause / resume / cancel)
│   └── RecordingFileManager.kt  # File paths, sidecar JSON, trash management
└── ui/
    ├── screens/                 # All composable screens
    ├── theme/                   # Color, Theme, Type
    └── viewmodel/AppViewModel.kt
```

---

## Roadmap

- [ ] Sub-folder library (folder tree, gallery view, move-to picker)
- [ ] Audio source tagging (Offline / Call / Online / Voice Note)
- [ ] Storage management screen (per-folder breakdown, bulk delete)
- [ ] Waveform thumbnails in gallery view
- [ ] Android Keystore for API key storage
- [ ] System-audio capture for online meetings (MediaProjection, API 29+)

---

## Contributing

PRs welcome. Please read `CLAUDE.md` for project conventions before submitting. Open an issue first for large changes.

---

## License

[MIT](LICENSE)

---

## Acknowledgements

- [Google Gemini API](https://ai.google.dev/) — AI transcription and analysis
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — UI toolkit
- [Material 3](https://m3.material.io/) — design system
- [Room](https://developer.android.com/jetpack/androidx/releases/room) — local database
