const btn = document.getElementById("btn");
const timerEl = document.getElementById("timer");
const statusEl = document.getElementById("status");

let timerInterval = null;

// ── Helpers ────────────────────────────────────────────────────────────────

function formatElapsed(ms) {
  const totalSec = Math.floor(ms / 1000);
  const h = Math.floor(totalSec / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  return `${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

function setStatus(text) {
  statusEl.textContent = text;
}

function startTimer(startedAt) {
  stopTimer();
  timerInterval = setInterval(() => {
    timerEl.textContent = formatElapsed(Date.now() - startedAt);
  }, 500);
}

function stopTimer() {
  if (timerInterval) {
    clearInterval(timerInterval);
    timerInterval = null;
  }
  timerEl.textContent = "0:00:00";
}

function setRecordingUI(active) {
  if (active) {
    btn.textContent = "Stop Recording";
    btn.className = "recording";
  } else {
    btn.textContent = "Start Recording";
    btn.className = "idle";
    stopTimer();
    setStatus("Ready");
  }
  btn.disabled = false;
}

// ── Initialise from persisted state ───────────────────────────────────────

chrome.storage.local.get("recordingState", ({ recordingState }) => {
  if (recordingState && recordingState.active) {
    setRecordingUI(true);
    startTimer(recordingState.startedAt || Date.now());
    setStatus("Recording…");
  } else {
    setRecordingUI(false);
  }
});

// ── Button handler ─────────────────────────────────────────────────────────

btn.addEventListener("click", async () => {
  btn.disabled = true;

  const { recordingState } = await chrome.storage.local.get("recordingState");
  const isRecording = recordingState && recordingState.active;

  if (!isRecording) {
    // ── START ────────────────────────────────────────────────────────────
    setStatus("Starting…");

    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });

    if (!tab || !tab.url || !tab.url.includes("meet.google.com")) {
      setStatus("⚠ Open a Google Meet tab first");
      btn.disabled = false;
      return;
    }

    chrome.runtime.sendMessage(
      { type: "START", tabId: tab.id, title: tab.title || "Meet recording" },
      (response) => {
        if (chrome.runtime.lastError || !response || !response.ok) {
          const msg = response?.error || chrome.runtime.lastError?.message || "Unknown error";
          setStatus(`Error: ${msg}`);
          btn.disabled = false;
          return;
        }
        setRecordingUI(true);
        startTimer(Date.now());
        setStatus("Recording…");
      }
    );
  } else {
    // ── STOP ─────────────────────────────────────────────────────────────
    setStatus("Stopping…");

    chrome.runtime.sendMessage({ type: "STOP" }, (response) => {
      if (chrome.runtime.lastError || !response || !response.ok) {
        const msg = response?.error || chrome.runtime.lastError?.message || "Unknown error";
        setStatus(`Error: ${msg}`);
        btn.disabled = false;
        return;
      }
      setRecordingUI(false);
      setStatus("Uploading complete.");
    });
  }
});
