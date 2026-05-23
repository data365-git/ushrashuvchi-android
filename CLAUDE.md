# CLAUDE.md

## 1. Project Summary

**Ushrashuvchi** ("Meeting" in Uzbek) is an Android AI meeting companion app. It records meetings, sends audio to the Gemini API for transcription and structuring, and presents the result as a summary, chapters, transcript, task checklist, and refined topic breakdown. Users can also ask the AI follow-up questions about any meeting via a chat interface.

---

## 2. Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM — `AppViewModel` (AndroidViewModel) + Repository |
| Database | Room (SQLite) via KSP-generated DAOs |
| Networking | Retrofit 2 + OkHttp + Moshi (KSP codegen) |
| AI / STT | Google Gemini API (`gemini-1.5-flash` → upgrading to `gemini-2.0-flash`) |
| Audio | `MediaRecorder` (`.3gp` / AMR_NB), `MediaPlayer` |
| Localization | In-app `AppStrings.kt` (EN / RU / UZ — no external locale files) |
| Testing | Robolectric + Roborazzi (screenshot tests) |
| Build | Gradle KTS, Secrets Gradle Plugin (`.env` → `BuildConfig`) |
| Platform | Android minSdk 24, targetSdk 36 |

---

## 3. Folder Structure

```
app/src/main/
├── java/com/example/
│   ├── MainActivity.kt              # Single-activity entry point, NavHost
│   ├── data/
│   │   ├── api/GeminiClient.kt      # Retrofit service + getAiResponse()
│   │   ├── dao/MeetingDao.kt        # Room DAO for all entities
│   │   ├── database/AppDatabase.kt  # Room database singleton
│   │   ├── localization/AppStrings.kt  # All UI strings in EN/RU/UZ
│   │   ├── model/Entities.kt        # Room entities: Meeting, TranscriptLine, Task, ChatMessage
│   │   └── repository/MeetingRepository.kt  # Business logic, Gemini pipeline, fallback data
│   └── ui/
│       ├── screens/AppScreens.kt    # ALL composable screens (single large file)
│       ├── theme/                   # Color.kt, Theme.kt, Type.kt
│       └── viewmodel/AppViewModel.kt  # State flows, recording, SharedPrefs, triggers
├── res/
│   ├── drawable/                    # Custom vector launcher icon (background + foreground)
│   └── mipmap-*/                    # Rasterized launcher icons per density
└── AndroidManifest.xml              # Permissions: RECORD_AUDIO, READ/WRITE_EXTERNAL_STORAGE
```

---

## 4. Environment Variables

```bash
# Required — injected via Secrets Gradle Plugin from .env into BuildConfig.GEMINI_API_KEY
GEMINI_API_KEY=          # Google Gemini API key; users can also override in-app via Settings

# Release signing (only needed for release builds)
KEYSTORE_PATH=           # Path to .jks keystore file
STORE_PASSWORD=          # Keystore password
KEY_PASSWORD=            # Key password (alias: "upload")
```

---

## 5. Running the Project

```bash
# Debug build + install to connected device (adb must see the device)
./gradlew :app:installDebug

# Build debug APK only
./gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release APK (requires KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD env vars)
./gradlew :app:assembleRelease

# Run unit + screenshot tests
./gradlew :app:test

# Check connected adb devices
adb devices
```

> **Signing note:** The debug build uses `debug.keystore` at the repo root (password: `android`). An APK installed via AI Studio is signed with a different debug key — uninstall the AI Studio APK before installing a locally-built one or you'll get `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

---

## 6. Conventions & Patterns

- **Single-file screens:** All UI lives in `AppScreens.kt`. It's intentionally one large file. Don't split it unless there's a very strong reason.
- **State in ViewModel:** All UI state is `MutableStateFlow` in `AppViewModel`. Screens collect via `collectAsState()`. No `remember` for business state.
- **SharedPreferences key:** `"ushrashuvchi_prefs"` — all persisted settings (API key, model names, prompts) use this.
- **Localization:** UI strings come from `AppStrings.kt`, not `strings.xml`. When adding any UI text, add it to all three locales (EN/RU/UZ) in `AppStrings.kt`. The single `strings.xml` only holds `app_name`.
- **JSON in DB:** `chaptersJson` and `refinedTranscriptJson` on `Meeting` are serialized Moshi JSON strings, not relational rows. Parse/serialize in the repository.
- **Gemini API key resolution:** `GeminiClient.getEffectiveApiKey()` prefers the runtime-set `customApiKey` over `BuildConfig.GEMINI_API_KEY`. The Settings screen writes to both SharedPrefs and `GeminiClient.setCustomApiKey()`.
- **Audio format:** MediaRecorder outputs `.3gp` (AMR_NB, ~12 kbps). Gemini MIME type must be `"audio/3gpp"`. No transcoding.
- **Moshi KSP:** All `@JsonClass(generateAdapter = true)` classes must be in the KSP source set. Don't use `KotlinJsonAdapterFactory` as the sole adapter for these — KSP generates faster adapters.

---

## 7. Important Notes

- **`AppScreens.kt` is ~2500+ lines.** Always read the relevant section before editing; line numbers shift frequently. Use search/grep to locate composables by name.
- **Inline base64 cap:** Gemini's inline audio path is capped at ~20 MB request body. AMR_NB at ~12 kbps → ~90 s/MB. Guard uploads with an 18 MB size check; fall back to topic-only generation if exceeded.
- **Fallback data (`createFallbackData`):** The repository has a hardcoded fallback with fictional participants (Marcus, Dave, Anvar). This triggers when Gemini returns unparseable JSON or network fails. It's intentional — don't remove it.
- **Demo seed data:** On first launch with an empty DB, `seedDemoData()` in the ViewModel populates 3 demo meetings. This only runs once (checked by `getMeetingsCount() == 0`).
- **`applicationId` is AI Studio-generated:** `com.aistudio.ushrashuvchi.zkpwxm`. Don't change it — doing so would orphan installs.
- **No Firebase yet:** `firebase.bom` and `firebase.ai` are in the dependency block but commented out. Don't activate them without a `google-services.json`.

---

## Template Propagation

This file (`~/secondbrain/CLAUDE-md template.md`) is the **master template**. After any edit to it:
- **Existing projects** — update the Rules section in every CLAUDE.md under `~/secondbrain`, preserving each project's sections 1–7.
- **New projects** — if a subfolder under `~/secondbrain` has code but no CLAUDE.md, create one by reading the project and filling in sections 1–7, then appending the full Rules section from this template.

Do both automatically, in parallel, without being asked.

---

## Keeping This File Current

Update CLAUDE.md when something **structurally meaningful** changes:
- New feature area or major dependency added
- Folder structure or naming convention changed
- New required environment variable
- Deployment process changed

**Do NOT update for:** bug fixes, style changes, copy tweaks, or anything that wouldn't matter to someone reading the project for the first time.

---

## Working in Parallel

**Default to parallel for ALL coding and planning work.** Before starting any multi-step task, decompose it into independent units and spawn one subagent per unit — fire ALL Task tool calls in a **single message** so they run simultaneously. Never serialize work that can run in parallel.

Sequential execution is only allowed when one task genuinely depends on another's output (e.g. step 2 needs the file step 1 created). For everything else — multi-file edits, multi-project changes, exploration + implementation, doc updates across files — parallelize.

Rule of thumb: if you catch yourself running tasks one after another, stop and ask "could these have run at the same time?" If yes, that's the wrong default.

---

## Pre-Push Sync Check (MANDATORY — runs BEFORE any commit/push/deploy)

Multiple developers may push to `main` between sessions. Local can fall behind silently. Claude must always sync with origin BEFORE any commit/push/deploy workflow — otherwise local work overwrites teammates' commits or push gets rejected and Claude force-resolves it the wrong way.

### Sequence (run in order, always)

**1. Refresh remote refs without merging:**
```bash
git fetch origin --prune
```

**2. Check if local is behind origin:**
```bash
git log HEAD..origin/main --oneline
git diff HEAD origin/main --stat
```

**3. If step 2 prints NOTHING** → local is current. Proceed to push.

**4. If step 2 prints any commits** → STOP. Do this:
- Print the commit list to the user verbatim ("origin/main has these N new commits from teammates: …").
- If there are uncommitted local changes:
  - Move them to a feature branch first: `git checkout -b sync-<timestamp>`, then `git add <specific files>`, then `git commit -m "WIP"`. **NEVER `git add -A`.**
- Rebase local onto origin/main:
  ```bash
  git pull --rebase origin main
  ```
- If rebase succeeds clean → proceed to push.
- If rebase produces conflicts → **STOP.** List each conflicted file. Ask the user how to resolve. **NEVER auto-pick "ours" or "theirs" without explicit instruction.**

**5. After conflict resolution**, verify the merged tree compiles before pushing:
```bash
./gradlew :app:assembleDebug
```

### Hard rules

- **NEVER `git push --force` or `--force-with-lease` to `main`/`master`.** If push is rejected, re-fetch and re-rebase — never force.
- **NEVER `git reset --hard origin/main` while uncommitted changes exist.** That deletes the user's work.
- **NEVER `git checkout .` or `git restore .`** to "clean up" — same risk.
- **NEVER rebase or merge silently when conflicts exist.** Resolution requires the user's input.
- **When in doubt, stop and ask.** A 30-second clarification beats a force-push that loses an hour of someone else's work.

### When this runs

- **Triggers on:** `deploy`, `push`, `merge to main`, `ship`, `git-shipper` agent invocation, `deployer` agent invocation, any prompt mentioning push-to-production.
- **Skipped only when:** the user explicitly says "skip sync check" or "just push, I already pulled".

---

## Model & Impact Routing

Before executing, declare in **one line** at the top of your reply:
> 🤖 `<haiku|sonnet|opus>` · 🎯 `<🟢low | 🟡med | 🔴high>` · ⚙️ `<one-line reason>`

**Model selection (cheapest tier that fits):**

| Use | For |
|-----|-----|
| **haiku** | Reads, greps, status checks, deploys, git workflows, env edits, find/replace, "continue"/"go" signals |
| **sonnet** | Code generation, debugging, multi-file features, refactors, plan decomposition |
| **opus** | Cross-system architecture, novel design, security-critical tradeoffs (rare) |

Rule: when unsure, use the cheaper tier. Escalate only if it struggles.

**Impact level (state blast radius for 🔴):**

| Tag | Means | Examples |
|-----|-------|----------|
| 🟢 low | Read-only / trivially undone | Read, Grep, status, Q&A |
| 🟡 med | Single-file / local config | Bug fix, doc edit, env var |
| 🔴 high | Multi-file / prod / irreversible | Deploy, merge to main, delete, secret rotation, 3+ files |

For 🔴 tasks: **list affected files/services before acting.**

---

## Expert Mode

Every task has a domain. Before responding, identify it — then think and respond as the most senior practitioner in that domain would. Do not mention this process, just embody it.

**What this means in practice:**
- Use the real frameworks and vocabulary of that domain, not generic assistant language
- Apply the quality bar of someone who has done this at the highest level — ask "would a principal-level practitioner sign off on this?"
- Ask the ONE question a real expert would ask before diving in (not five — one)
- Push back the way they would: directly, briefly, with a better direction
- If a task spans multiple domains, split your thinking per domain — don't blend into mush

**Domain-specific instincts to always apply:**

| Domain | What a world-class practitioner actually does differently |
|--------|----------------------------------------------------------|
| **Design / UX** | Solves confusion before beauty. Asks "what decision does the user need to make here?" Catches hierarchy and flow problems before pixel details. |
| **Product** | Ties every feature to a user problem and a measurable outcome. Rejects solutions without a clear success metric. |
| **Engineering** | Thinks failure modes, rollback, and observability — not just "does it work." Flags scale and maintenance cost upfront. |
| **DevOps / Infra** | Asks about blast radius before touching prod. Never ships without a health check and a rollback plan. |
| **Marketing / Growth** | Anchors every decision to conversion or retention. Challenges vanity metrics. |
| **Strategy / Leadership** | Thinks in systems and second-order effects, not just immediate outputs. |

For any domain not listed above: find the equivalent senior practitioner instinct and apply it.

---

## Recap Table at the End (when work was actually done)

### 🚫 DO NOT show the recap table in these cases — this rule is absolute:

1. **Plan mode** — when ExitPlanMode tool is being used, or any reply that is a proposal/plan to be approved before execution. NO TABLE.
2. **Pure planning/discussion sessions** — when the reply is only describing what *would* be done, not what *was* done. NO TABLE.
3. **Brainstorming, Q&A, "what is X", clarifying questions, advice** — NO TABLE.
4. **Trivial single-turn replies** — greetings, acknowledgments, one-line answers. NO TABLE.

**The test:** Did this reply actually change files, run commands, or produce output?
- **NO** → no table. Period. Even if the user asks "anything left?", answer in plain prose.
- **YES** → use the table below.

### ✅ When work WAS done, end the reply with this table:

```
| Status | Task | Notes |
|--------|------|-------|
| ✅ Done | [what was completed] | [file path / command / result] |
| ⏳ Pending | [what's still to do] | [why — waiting on user input, blocked, deferred] |
| ⚠️ Skipped | [what was not done] | [reason] |
```

Rules for the table:
- Group related sub-steps into one row — don't bloat the table
- Each "Notes" cell under 80 chars
- Omit Pending/Skipped rows if there are none
- Table goes at the very bottom of the reply, not the top

**One more time:** if no files were edited and no commands were executed in this reply, there is no table. The recap exists only to summarize concrete work — not to summarize a plan.

---

## Multi-Language / i18n Rule

This project has 3 interface languages: **EN, RU, UZ** — all defined in `AppStrings.kt`.

**Every UI string change touches ALL three languages — no exceptions.**

- When adding a new label, button, error, tooltip, or any user-facing text → add it to all three locale objects in `AppStrings.kt`
- When editing an existing string → update the matching key in all three
- When deleting a string → remove it from all three
- Write proper translations for RU and UZ — not English placeholders. Mark uncertain ones with a `// TRANSLATE` comment.

**Never leave a key missing in one locale.** The app renders directly from these objects with no fallback.

---

## Behavioral Guidelines

These rules reduce common LLM coding mistakes. They bias toward caution — use judgment on trivial tasks.

### 1. Think Before Coding

**Don't assume. Surface tradeoffs. Ask when unclear.**

- State your assumptions explicitly before implementing.
- If multiple interpretations exist, name them — don't pick silently.
- If a simpler approach exists, say so and push back.
- If something is genuinely unclear, stop and ask. Don't guess.

### 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "extensibility" that wasn't requested.
- No error handling for scenarios that can't happen.
- If you wrote 200 lines and it could be 50, rewrite it.

> Ask: "Would a senior engineer call this overcomplicated?" If yes — simplify.

### 3. Surgical Changes

**Touch only what you must.**

When editing existing code:
- Don't improve adjacent code, comments, or formatting unless asked.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you spot unrelated dead code, mention it — don't delete it.

When your changes create orphans:
- Remove imports, variables, and functions that **your** changes made unused.
- Don't remove pre-existing dead code unless explicitly asked.

> Test: every changed line should trace directly to the user's request.

### 4. Verify Before Reporting Done

**Define success criteria upfront. Loop until verified.**

For multi-step tasks, state a brief plan first:
```
1. [What] → verify: [how to confirm it worked]
2. [What] → verify: [how to confirm it worked]
3. [What] → verify: [how to confirm it worked]
```

Run the check before saying "done." If you can't verify (e.g. needs a device), say so explicitly and describe what the user should check.

---

**These guidelines are working when:** diffs are clean, rewrites are rare, and questions come before implementation — not after.
