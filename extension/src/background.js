// TODO: set to your real Railway URL
const API = "https://ushrashuvchi-backend-production.up.railway.app";

import { ensureToken } from "./auth.js";

// Ensure the offscreen document exists (created once per service-worker lifetime).
async function ensureOffscreen() {
  const existing = await chrome.offscreen.hasDocument();
  if (existing) return;
  await chrome.offscreen.createDocument({
    url: chrome.runtime.getURL("offscreen.html"),
    reasons: ["USER_MEDIA"],
    justification: "Capture tab audio/video and mix with microphone for recording.",
  });
}

// Create a meeting on the backend and return its id.
async function createMeeting(token, title) {
  const clientId = Math.floor(Date.now() / 1000);
  const res = await fetch(`${API}/api/v1/meetings`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      clientId,
      title,
      date: Date.now(),
      durationSeconds: 0,
      status: "RECORDING",
      audioSource: "ONLINE_MEET",
    }),
  });

  if (!res.ok) {
    throw new Error(`Create meeting failed: ${res.status} ${res.statusText}`);
  }

  const { id } = await res.json();
  return id;
}

chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  if (msg.type === "START") {
    handleStart(msg, sendResponse);
    return true; // keep channel open for async sendResponse
  }

  if (msg.type === "STOP") {
    handleStop(sendResponse);
    return true;
  }
});

async function handleStart({ tabId, title }, sendResponse) {
  try {
    const token = await ensureToken();
    const meetingId = await createMeeting(token, title || "Meet recording");

    // Acquire a stream ID for the target tab (must be called in service worker).
    const streamId = await new Promise((resolve, reject) => {
      chrome.tabCapture.getMediaStreamId({ targetTabId: tabId }, (id) => {
        if (chrome.runtime.lastError) {
          reject(new Error(chrome.runtime.lastError.message));
        } else {
          resolve(id);
        }
      });
    });

    await ensureOffscreen();

    chrome.runtime.sendMessage({
      target: "offscreen",
      type: "OFFSCREEN_START",
      streamId,
      meetingId,
      token,
      api: API,
    });

    // Persist recording state for popup.
    await chrome.storage.local.set({ recordingState: { active: true, meetingId, startedAt: Date.now() } });

    sendResponse({ ok: true, meetingId });
  } catch (err) {
    console.error("[background] START error:", err);
    sendResponse({ ok: false, error: err.message });
  }
}

async function handleStop(sendResponse) {
  try {
    const existing = await chrome.offscreen.hasDocument();
    if (existing) {
      chrome.runtime.sendMessage({ target: "offscreen", type: "OFFSCREEN_STOP" });
    }
    await chrome.storage.local.set({ recordingState: { active: false } });
    sendResponse({ ok: true });
  } catch (err) {
    console.error("[background] STOP error:", err);
    sendResponse({ ok: false, error: err.message });
  }
}
