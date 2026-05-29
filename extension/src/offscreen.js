/**
 * Offscreen document — runs in a real browser context so getUserMedia is available.
 *
 * Flow:
 *  1. Receive OFFSCREEN_START → capture tab stream, capture mic, mix audio.
 *  2. MediaRecorder slices into 5-second chunks → PUT .../video/append?index=N
 *  3. On OFFSCREEN_STOP → recorder.stop() → POST .../video/complete.
 */

let recorder = null;
let audioCtx = null;

chrome.runtime.onMessage.addListener((msg) => {
  if (msg.target !== "offscreen") return;

  if (msg.type === "OFFSCREEN_START") {
    startRecording(msg).catch((err) => {
      console.error("[offscreen] startRecording error:", err);
    });
  }

  if (msg.type === "OFFSCREEN_STOP") {
    if (recorder && recorder.state !== "inactive") {
      recorder.stop();
    }
  }
});

async function startRecording({ streamId, meetingId, token, api }) {
  // ── Tab stream (audio + video) ─────────────────────────────────────────────
  const tabStream = await navigator.mediaDevices.getUserMedia({
    audio: {
      mandatory: {
        chromeMediaSource: "tab",
        chromeMediaSourceId: streamId,
      },
    },
    video: {
      mandatory: {
        chromeMediaSource: "tab",
        chromeMediaSourceId: streamId,
        maxWidth: 1280,
        maxHeight: 720,
        maxFrameRate: 30,
      },
    },
  });

  // ── Microphone stream ──────────────────────────────────────────────────────
  const micStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });

  // ── Audio mixing ───────────────────────────────────────────────────────────
  audioCtx = new AudioContext();

  const tabAudioSource = audioCtx.createMediaStreamSource(tabStream);
  const micAudioSource = audioCtx.createMediaStreamSource(micStream);

  const destination = audioCtx.createMediaStreamDestination();
  tabAudioSource.connect(destination);
  micAudioSource.connect(destination);

  // Route tab audio to speakers so the user still hears the meeting.
  tabAudioSource.connect(audioCtx.destination);

  // ── Compose final stream (tab video + mixed audio) ─────────────────────────
  const [tabVideoTrack] = tabStream.getVideoTracks();
  const [mixedAudioTrack] = destination.stream.getAudioTracks();
  const composedStream = new MediaStream([tabVideoTrack, mixedAudioTrack]);

  // ── Notify backend that video upload is starting ───────────────────────────
  await fetch(`${api}/api/v1/meetings/${meetingId}/video/start`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  });

  // ── MediaRecorder ──────────────────────────────────────────────────────────
  recorder = new MediaRecorder(composedStream, {
    mimeType: "video/webm;codecs=vp9,opus",
    videoBitsPerSecond: 1_200_000,
    audioBitsPerSecond: 128_000,
  });

  let chunkIndex = 0;

  recorder.ondataavailable = async (event) => {
    if (!event.data || event.data.size === 0) return;

    const index = chunkIndex++;
    const buffer = await event.data.arrayBuffer();

    try {
      const res = await fetch(
        `${api}/api/v1/meetings/${meetingId}/video/append?index=${index}`,
        {
          method: "PUT",
          headers: {
            Authorization: `Bearer ${token}`,
            "Content-Type": "application/octet-stream",
          },
          body: buffer,
        }
      );
      if (!res.ok) {
        console.warn(`[offscreen] append chunk ${index} failed: ${res.status}`);
      }
    } catch (err) {
      console.error(`[offscreen] append chunk ${index} error:`, err);
    }
  };

  recorder.onstop = async () => {
    try {
      await fetch(`${api}/api/v1/meetings/${meetingId}/video/complete`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
      });
    } catch (err) {
      console.error("[offscreen] video/complete error:", err);
    }

    // Clean up.
    tabStream.getTracks().forEach((t) => t.stop());
    micStream.getTracks().forEach((t) => t.stop());
    audioCtx.close();

    // Remove the offscreen document.
    await chrome.offscreen.closeDocument();
  };

  // Start slicing every 5 seconds.
  recorder.start(5000);
}
