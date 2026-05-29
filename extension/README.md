# Ushrashuvchi – Chrome Meet Recorder Extension

Records a Google Meet tab (audio + video, mixed with your microphone) and
live-uploads 5-second chunks to the Ushrashuvchi backend.

---

## Before loading

### 1. Add icon PNGs

Chrome requires real icon files. Copy `icons/README.txt` for details — you need:

```
extension/icons/16.png
extension/icons/48.png
extension/icons/128.png
```

Resize the app's mic art (`app/src/main/res/drawable/ic_launcher_foreground.png`)
to 16, 48, and 128 px and drop the files here.

### 2. Set the API base URL

The backend URL appears in **two** places (both already set to the Railway domain):

| File | Constant |
|------|----------|
| `src/background.js` line 2 | `const API = "..."` |
| `src/auth.js` line 2        | `const API = "..."` |

If your Railway deployment URL differs, update both to match before loading the extension.

---

## Loading the extension (unpacked)

1. Open Chrome and navigate to `chrome://extensions/`.
2. Enable **Developer mode** (toggle, top-right).
3. Click **Load unpacked**.
4. Select this `extension/` folder.
5. The extension icon appears in your toolbar.

---

## Using the extension

1. Open (or join) a Google Meet at `https://meet.google.com/…`.
2. Click the extension icon in the Chrome toolbar.
3. Click **Start Recording**.
   - Chrome will ask for microphone permission the first time.
   - A tab-capture permission prompt may appear — allow it.
4. The popup shows an elapsed timer and "Recording…" status.
5. Click **Stop Recording** when the meeting ends.
   - The final chunk is uploaded and the backend is notified via `video/complete`.

---

## Architecture overview

```
popup.js  ──message──▶  background.js (service worker)
                              │  ensureToken() – device registration
                              │  createMeeting() – POST /api/v1/meetings
                              │  tabCapture.getMediaStreamId()
                              ▼
                         offscreen.js (offscreen document)
                              │  getUserMedia(tab) + getUserMedia(mic)
                              │  AudioContext mixing → MediaRecorder
                              │  PUT /api/v1/meetings/:id/video/append?index=N  (every 5s)
                              ▼
                         POST /api/v1/meetings/:id/video/complete  (on stop)
```

---

## Notes

- Audio format: `video/webm;codecs=vp9,opus` at 1.2 Mbps video / 128 kbps audio.
- Tab audio is **also routed to the user's speakers** so the meeting is still audible during recording.
- Device token is stored in `chrome.storage.local` after first registration and reused for all subsequent meetings.
- Recording state survives popup close/reopen via `chrome.storage.local`.
