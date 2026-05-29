// TODO: set to your real Railway URL
const API = "https://ushrashuvchi-backend-production.up.railway.app";

/**
 * Returns a valid Bearer token. On first call (no token stored) it registers
 * this device with the backend and persists the returned deviceId + token.
 *
 * Backend contract:
 *   POST /api/v1/devices/register  { name: string }
 *   → { deviceId: string, token: string }
 */
export async function ensureToken() {
  const { token } = await chrome.storage.local.get("token");
  if (token) return token;

  const res = await fetch(`${API}/api/v1/devices/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name: "Chrome extension" }),
  });

  if (!res.ok) {
    throw new Error(`Device registration failed: ${res.status} ${res.statusText}`);
  }

  const { deviceId, token: newToken } = await res.json();
  await chrome.storage.local.set({ deviceId, token: newToken });
  return newToken;
}
