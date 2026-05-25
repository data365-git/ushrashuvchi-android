package com.example.server.share

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.webViewerRoute() {
    get("/s/{token}") {
        val token = call.parameters["token"] ?: run {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.respondText(viewerHtml(token), ContentType.Text.Html)
    }

    get("/s/{token}/password") {
        val token = call.parameters["token"] ?: return@get
        call.respondText(passwordPromptHtml(token), ContentType.Text.Html)
    }
}

private fun viewerHtml(token: String): String = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Meeting</title>
  <style>
    :root {
      --bg: #F1F5F9;
      --surface: #FFFFFF;
      --accent: #1D4ED8;
      --text: #0F172A;
      --text-muted: #64748B;
      --outline: #E2E8F0;
      --error: #DC2626;
      --refined-bg: #DBEAFE;
      --refined-text: #1E40AF;
      --call-bg: #FEE2E2;
      --call-text: #DC2626;
      --offline-bg: #D1FAE5;
      --offline-text: #047857;
      --online-bg: #FED7AA;
      --online-text: #C2410C;
      --voice-bg: #FCE7F3;
      --voice-text: #BE185D;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      background: var(--bg);
      color: var(--text);
      min-height: 100vh;
      padding-bottom: 100px;
    }
    .container { max-width: 720px; margin: 0 auto; padding: 16px; }
    .header {
      background: var(--surface);
      border-radius: 20px;
      padding: 20px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06);
      margin-bottom: 12px;
    }
    .title { font-size: 22px; font-weight: 700; margin-bottom: 8px; line-height: 1.3; }
    .meta { display: flex; align-items: center; gap: 10px; color: var(--text-muted); font-size: 13px; flex-wrap: wrap; }
    .source-badge {
      display: inline-flex; align-items: center; gap: 4px;
      padding: 4px 10px; border-radius: 999px; font-weight: 600; font-size: 12px;
    }
    .src-CALL { background: var(--call-bg); color: var(--call-text); }
    .src-OFFLINE_MEET { background: var(--offline-bg); color: var(--offline-text); }
    .src-ONLINE_MEET { background: var(--online-bg); color: var(--online-text); }
    .src-VOICE_MEMO { background: var(--voice-bg); color: var(--voice-text); }
    .password-prompt {
      background: var(--surface); border-radius: 20px; padding: 32px; text-align: center; margin-top: 60px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06);
    }
    .password-prompt input {
      width: 100%; padding: 12px; border: 1px solid var(--outline); border-radius: 12px;
      font-size: 16px; margin: 16px 0;
    }
    .btn {
      background: var(--accent); color: white; padding: 12px 24px; border: none;
      border-radius: 12px; font-size: 15px; font-weight: 600; cursor: pointer; width: 100%;
    }
    .btn:hover { opacity: 0.9; }
    .error-state {
      background: var(--surface); border-radius: 20px; padding: 40px; text-align: center; margin-top: 60px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06);
    }
    .error-icon { font-size: 48px; color: var(--error); margin-bottom: 12px; }
    .tabs {
      display: flex; background: var(--surface); border-radius: 20px 20px 0 0; overflow: hidden;
      margin-bottom: 0; box-shadow: 0 -2px 8px rgba(0,0,0,0.04);
      position: sticky; top: 0; z-index: 10;
    }
    .tab {
      flex: 1; padding: 14px; text-align: center; cursor: pointer; font-size: 14px;
      font-weight: 600; color: var(--text-muted); border-bottom: 3px solid transparent;
      transition: all 0.2s;
    }
    .tab.active { color: var(--accent); border-bottom-color: var(--accent); }
    .tab-content {
      background: var(--surface); border-radius: 0 0 20px 20px; padding: 20px;
      min-height: 200px; box-shadow: 0 2px 8px rgba(0,0,0,0.06);
    }
    .tab-pane { display: none; }
    .tab-pane.active { display: block; }
    .markdown { line-height: 1.6; }
    .markdown h1, .markdown h2, .markdown h3 { margin: 16px 0 8px; }
    .markdown ul, .markdown ol { padding-left: 20px; margin: 8px 0; }
    .markdown li { margin: 4px 0; }
    .markdown p { margin: 8px 0; }
    .markdown strong { font-weight: 600; }
    .markdown code { background: var(--outline); padding: 2px 6px; border-radius: 4px; font-size: 0.9em; }
    .transcript-line {
      padding: 12px 0; border-bottom: 1px solid var(--outline);
    }
    .transcript-line:last-child { border-bottom: none; }
    .transcript-line.active { background: rgba(29,78,216,0.08); border-radius: 8px; padding: 12px; }
    .transcript-meta {
      display: flex; gap: 12px; align-items: center; margin-bottom: 4px;
      font-size: 12px; color: var(--text-muted); font-weight: 600;
    }
    .transcript-speaker { color: var(--accent); }
    .transcript-text { font-size: 14px; line-height: 1.5; }
    .topic-card {
      background: var(--bg); border-radius: 12px; padding: 16px; margin-bottom: 12px;
    }
    .topic-title { font-size: 16px; font-weight: 700; margin-bottom: 8px; }
    .topic-summary { font-size: 13px; color: var(--text-muted); margin-bottom: 8px; }
    .topic-section { margin-top: 12px; }
    .topic-section-label {
      font-size: 11px; font-weight: 700; color: var(--accent); text-transform: uppercase;
      margin-bottom: 4px; letter-spacing: 0.5px;
    }
    .task-row {
      display: flex; align-items: flex-start; gap: 12px; padding: 12px 0;
      border-bottom: 1px solid var(--outline);
    }
    .task-row:last-child { border-bottom: none; }
    .task-check {
      flex-shrink: 0; width: 22px; height: 22px; border-radius: 50%; border: 2px solid var(--outline);
      margin-top: 2px; display: flex; align-items: center; justify-content: center;
    }
    .task-check.done { background: var(--accent); border-color: var(--accent); color: white; }
    .task-title { font-weight: 500; }
    .task-assignee { font-size: 12px; color: var(--text-muted); margin-top: 2px; }
    .player {
      position: fixed; bottom: 0; left: 0; right: 0; background: var(--surface);
      padding: 12px 16px; box-shadow: 0 -2px 12px rgba(0,0,0,0.08);
      display: flex; align-items: center; gap: 12px; z-index: 20;
    }
    .player-info { flex: 1; overflow: hidden; }
    .player-title { font-weight: 600; font-size: 14px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .player-time { font-size: 12px; color: var(--text-muted); }
    audio { display: block; width: 100%; margin-top: 4px; height: 36px; }
    .empty {
      text-align: center; padding: 40px 20px; color: var(--text-muted); font-size: 14px;
    }
    .loading { text-align: center; padding: 40px; color: var(--text-muted); }
    .ask-section { background: var(--surface); border-radius: 20px; padding: 20px; margin-top: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
    .ask-section h3 { font-size: 16px; margin-bottom: 12px; }
    .ask-history { display: flex; flex-direction: column; gap: 10px; margin-bottom: 14px; max-height: 320px; overflow-y: auto; }
    .ask-bubble { padding: 10px 14px; border-radius: 14px; max-width: 85%; font-size: 14px; line-height: 1.5; word-wrap: break-word; }
    .ask-user { background: var(--accent); color: white; align-self: flex-end; border-bottom-right-radius: 4px; }
    .ask-bot { background: var(--bg); color: var(--text); align-self: flex-start; border-bottom-left-radius: 4px; }
    .ask-input-row { display: flex; gap: 8px; }
    .ask-input { flex: 1; padding: 12px; border: 1px solid var(--outline); border-radius: 14px; font-size: 14px; font-family: inherit; }
    .ask-input:focus { outline: none; border-color: var(--accent); }
    .ask-btn { padding: 12px 18px; background: var(--accent); color: white; border: none; border-radius: 14px; font-size: 14px; font-weight: 600; cursor: pointer; }
    .ask-btn:disabled { opacity: 0.5; cursor: wait; }
    .ask-loading { font-size: 12px; color: var(--text-muted); font-style: italic; padding: 8px 14px; }
    .footer {
      text-align: center; margin-top: 32px; color: var(--text-muted); font-size: 11px;
    }
  </style>
</head>
<body>
  <div class="container">
    <div id="root">
      <div class="loading">Loading meeting...</div>
    </div>
  </div>
  <div class="footer">Powered by Ushrashuvchi</div>

  <script>
    const token = '$token';
    const API_BASE = '';
    let meetingData = null;
    let storedPassword = sessionStorage.getItem('share_pw_' + token) || '';

    async function fetchMeeting() {
      try {
        const headers = {};
        if (storedPassword) headers['X-Share-Password'] = storedPassword;
        const resp = await fetch(API_BASE + '/api/v1/public/' + token, { headers });
        if (resp.status === 401) { renderPasswordPrompt(); return; }
        if (resp.status === 410) { renderError('This share link has expired or been revoked.'); return; }
        if (resp.status === 404) { renderError('Share not found.'); return; }
        if (!resp.ok) { renderError('Error loading meeting (HTTP ' + resp.status + ')'); return; }
        meetingData = await resp.json();
        renderViewer();
      } catch (e) {
        renderError('Network error: ' + e.message);
      }
    }

    function renderPasswordPrompt() {
      document.getElementById('root').innerHTML = `${'$'}{
        '<div class="password-prompt">' +
        '<h2 style="margin-bottom: 8px;">🔒 Password required</h2>' +
        '<p style="color: var(--text-muted); font-size: 14px;">This meeting share is password-protected.</p>' +
        '<input type="password" id="pwInput" placeholder="Enter password" autofocus>' +
        '<button class="btn" onclick="submitPassword()">Unlock</button>' +
        '</div>'
      }`;
      document.getElementById('pwInput').addEventListener('keydown', e => {
        if (e.key === 'Enter') submitPassword();
      });
    }

    function submitPassword() {
      storedPassword = document.getElementById('pwInput').value;
      sessionStorage.setItem('share_pw_' + token, storedPassword);
      document.getElementById('root').innerHTML = '<div class="loading">Loading...</div>';
      fetchMeeting();
    }

    function renderError(msg) {
      document.getElementById('root').innerHTML =
        '<div class="error-state">' +
        '<div class="error-icon">⚠</div>' +
        '<h2 style="margin-bottom: 8px;">Unable to load</h2>' +
        '<p style="color: var(--text-muted);">' + msg + '</p>' +
        '</div>';
    }

    function formatDate(ms) {
      return new Date(ms).toLocaleString();
    }
    function formatDuration(s) {
      const m = Math.floor(s / 60); const sec = s % 60;
      return m + ':' + String(sec).padStart(2, '0');
    }
    function formatTs(ms) {
      const s = Math.floor(ms / 1000);
      const m = Math.floor(s / 60); const sec = s % 60;
      return m + ':' + String(sec).padStart(2, '0');
    }
    function sourceLabel(src) {
      const map = { CALL: '📞 Call', OFFLINE_MEET: '👥 Offline', ONLINE_MEET: '💻 Online', VOICE_MEMO: '🎤 Voice memo' };
      return map[src] || src;
    }
    function escapeHtml(s) {
      return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
    function renderMarkdown(md) {
      if (!md) return '<p class="empty">No content</p>';
      let h = escapeHtml(md);
      h = h.replace(/^### (.+)${'$'}/gm, '<h3>${'$'}1</h3>');
      h = h.replace(/^## (.+)${'$'}/gm, '<h2>${'$'}1</h2>');
      h = h.replace(/^# (.+)${'$'}/gm, '<h1>${'$'}1</h1>');
      h = h.replace(/\*\*(.+?)\*\*/g, '<strong>${'$'}1</strong>');
      h = h.replace(/\*(.+?)\*/g, '<em>${'$'}1</em>');
      h = h.replace(/^- (.+)${'$'}/gm, '<li>${'$'}1</li>');
      h = h.replace(/(<li>.*<\/li>\n?)+/g, m => '<ul>' + m + '</ul>');
      h = h.replace(/\n\n/g, '</p><p>');
      h = '<p>' + h + '</p>';
      h = h.replace(/<p><(h\d|ul|ol)>/g, '<${'$'}1>');
      h = h.replace(/<\/(h\d|ul|ol)><\/p>/g, '</${'$'}1>');
      return h;
    }

    function renderViewer() {
      const m = meetingData;
      const hasAudio = m.hasAudio;
      const audioUrl = hasAudio ? (API_BASE + '/api/v1/public/' + token + '/audio') : null;

      const html =
        '<div class="header">' +
          '<div class="title">' + escapeHtml(m.title || 'Untitled meeting') + '</div>' +
          '<div class="meta">' +
            '<span class="source-badge src-' + m.audioSource + '">' + sourceLabel(m.audioSource) + '</span>' +
            '<span>' + formatDate(m.date) + '</span>' +
            '<span>· ' + formatDuration(m.durationSeconds) + '</span>' +
          '</div>' +
        '</div>' +
        '<div class="tabs">' +
          '<div class="tab active" data-tab="summary" onclick="switchTab(\'summary\')">Summary</div>' +
          '<div class="tab" data-tab="refined" onclick="switchTab(\'refined\')">Refined</div>' +
          '<div class="tab" data-tab="transcript" onclick="switchTab(\'transcript\')">Transcript</div>' +
          '<div class="tab" data-tab="tasks" onclick="switchTab(\'tasks\')">Tasks</div>' +
        '</div>' +
        '<div class="tab-content">' +
          '<div class="tab-pane active" id="pane-summary">' +
            '<div class="markdown">' + renderMarkdown(m.summary) + '</div>' +
          '</div>' +
          '<div class="tab-pane" id="pane-refined">' + renderRefined(m.refinedJson) + '</div>' +
          '<div class="tab-pane" id="pane-transcript">' + renderTranscript(m.transcript) + '</div>' +
          '<div class="tab-pane" id="pane-tasks">' + renderTasks(m.tasks) + '</div>' +
        '</div>';

      let player = '';
      if (audioUrl) {
        player =
          '<div class="player">' +
            '<div class="player-info">' +
              '<div class="player-title">' + escapeHtml(m.title) + '</div>' +
              '<audio id="audioEl" controls preload="metadata">' +
                '<source src="' + audioUrl + '" type="audio/mp4">' +
              '</audio>' +
            '</div>' +
          '</div>';
      }

      // Ask AI on public shares — disabled for v1.
      // Flip ENABLE_PUBLIC_ASK_AI to true (and ensure GEMINI_API_KEY is set on the backend) to enable.
      const ENABLE_PUBLIC_ASK_AI = false;
      const askSection = ENABLE_PUBLIC_ASK_AI
        ? '<div class="ask-section">' +
            '<h3>🤖 Ask AI about this meeting</h3>' +
            '<div class="ask-history" id="askHistory"></div>' +
            '<div class="ask-input-row">' +
              '<input type="text" class="ask-input" id="askInput" placeholder="Ask anything about this meeting…" />' +
              '<button class="ask-btn" id="askBtn">Ask</button>' +
            '</div>' +
          '</div>'
        : '';

      document.getElementById('root').innerHTML = html + askSection + player;

      const audio = document.getElementById('audioEl');
      if (audio) {
        audio.addEventListener('timeupdate', () => highlightActiveLine(audio.currentTime * 1000));
      }
      if (ENABLE_PUBLIC_ASK_AI) {
        const askBtn = document.getElementById('askBtn');
        const askInput = document.getElementById('askInput');
        if (askBtn) askBtn.addEventListener('click', submitAsk);
        if (askInput) askInput.addEventListener('keydown', e => { if (e.key === 'Enter') submitAsk(); });
      }
    }

    async function submitAsk() {
      const input = document.getElementById('askInput');
      const btn = document.getElementById('askBtn');
      const history = document.getElementById('askHistory');
      const q = input.value.trim();
      if (!q) return;
      input.value = '';
      btn.disabled = true;
      const userBubble = document.createElement('div');
      userBubble.className = 'ask-bubble ask-user';
      userBubble.textContent = q;
      history.appendChild(userBubble);
      const loader = document.createElement('div');
      loader.className = 'ask-loading';
      loader.textContent = 'Thinking…';
      history.appendChild(loader);
      history.scrollTop = history.scrollHeight;
      try {
        const headers = { 'Content-Type': 'application/json' };
        if (storedPassword) headers['X-Share-Password'] = storedPassword;
        const resp = await fetch(API_BASE + '/api/v1/public/' + token + '/ask', {
          method: 'POST',
          headers: headers,
          body: JSON.stringify({ question: q })
        });
        const data = await resp.json();
        loader.remove();
        const bot = document.createElement('div');
        bot.className = 'ask-bubble ask-bot';
        if (resp.ok) {
          bot.textContent = data.answer;
        } else {
          bot.style.background = '#FEE2E2';
          bot.style.color = '#DC2626';
          bot.textContent = data.error || ('Error ' + resp.status);
        }
        history.appendChild(bot);
        history.scrollTop = history.scrollHeight;
      } catch (e) {
        loader.remove();
        const err = document.createElement('div');
        err.className = 'ask-bubble ask-bot';
        err.style.background = '#FEE2E2';
        err.style.color = '#DC2626';
        err.textContent = 'Network error: ' + e.message;
        history.appendChild(err);
      } finally {
        btn.disabled = false;
        input.focus();
      }
    }

    function renderRefined(jsonStr) {
      if (!jsonStr) return '<div class="empty">No refined topics</div>';
      try {
        const topics = JSON.parse(jsonStr);
        if (!Array.isArray(topics) || topics.length === 0) return '<div class="empty">No refined topics</div>';
        return topics.map(t => {
          let html = '<div class="topic-card">';
          html += '<div class="topic-title">' + escapeHtml(t.title) + '</div>';
          html += '<div class="topic-summary">' + escapeHtml(t.summary || '') + '</div>';
          if ((t.keyPoints || []).length) {
            html += '<div class="topic-section"><div class="topic-section-label">Key Points</div><ul>' +
              (t.keyPoints || []).map(k => '<li>' + escapeHtml(k) + '</li>').join('') +
              '</ul></div>';
          }
          if ((t.decisions || []).length) {
            html += '<div class="topic-section"><div class="topic-section-label">Decisions</div><ul>' +
              (t.decisions || []).map(k => '<li>' + escapeHtml(k) + '</li>').join('') +
              '</ul></div>';
          }
          if ((t.openQuestions || []).length) {
            html += '<div class="topic-section"><div class="topic-section-label">Open Questions</div><ul>' +
              (t.openQuestions || []).map(k => '<li>' + escapeHtml(k) + '</li>').join('') +
              '</ul></div>';
          }
          html += '</div>';
          return html;
        }).join('');
      } catch (e) { return '<div class="empty">Unable to parse refined topics</div>'; }
    }

    function renderTranscript(lines) {
      if (!lines || lines.length === 0) return '<div class="empty">No transcript</div>';
      return lines.map((l, i) =>
        '<div class="transcript-line" id="line-' + i + '" data-start="' + l.tsStartMs + '" data-end="' + l.tsEndMs + '">' +
          '<div class="transcript-meta">' +
            '<span class="transcript-speaker">' + escapeHtml(l.speaker) + '</span>' +
            '<span>' + formatTs(l.tsStartMs) + '</span>' +
          '</div>' +
          '<div class="transcript-text">' + escapeHtml(l.text) + '</div>' +
        '</div>'
      ).join('');
    }

    function highlightActiveLine(currentMs) {
      const lines = document.querySelectorAll('.transcript-line');
      lines.forEach(line => {
        const start = parseInt(line.dataset.start);
        const end = parseInt(line.dataset.end);
        if (currentMs >= start && currentMs <= end) {
          if (!line.classList.contains('active')) {
            line.classList.add('active');
            line.scrollIntoView({ behavior: 'smooth', block: 'center' });
          }
        } else {
          line.classList.remove('active');
        }
      });
    }

    function renderTasks(tasks) {
      if (!tasks || tasks.length === 0) return '<div class="empty">No tasks</div>';
      return tasks.map(t =>
        '<div class="task-row">' +
          '<div class="task-check ' + (t.isCompleted ? 'done' : '') + '">' + (t.isCompleted ? '✓' : '') + '</div>' +
          '<div>' +
            '<div class="task-title">' + escapeHtml(t.title) + '</div>' +
            (t.assignee && t.assignee !== 'Unassigned' ? '<div class="task-assignee">@' + escapeHtml(t.assignee) + '</div>' : '') +
          '</div>' +
        '</div>'
      ).join('');
    }

    function switchTab(name) {
      document.querySelectorAll('.tab').forEach(t => t.classList.toggle('active', t.dataset.tab === name));
      document.querySelectorAll('.tab-pane').forEach(p => p.classList.toggle('active', p.id === 'pane-' + name));
    }

    fetchMeeting();
  </script>
</body>
</html>
""".trimIndent()

private fun passwordPromptHtml(token: String): String = viewerHtml(token)
