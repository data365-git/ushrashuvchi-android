package com.example.server.dashboard

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.dashboardRoutes() {
    get("/dashboard") {
        call.respondText(dashboardHtml(), ContentType.Text.Html)
    }
}

@Suppress("LongMethod")
private fun dashboardHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Ushrashuvchi Dashboard</title>
  <style>
    :root {
      --bg: #F1F5F9; --surface: #FFFFFF; --accent: #1D4ED8; --accent-light: #DBEAFE;
      --text: #0F172A; --text-muted: #64748B; --outline: #E2E8F0; --error: #DC2626;
      --call-bg: #FEE2E2; --call-text: #DC2626;
      --offline-bg: #D1FAE5; --offline-text: #047857;
      --online-bg: #FED7AA; --online-text: #C2410C;
      --voice-bg: #FCE7F3; --voice-text: #BE185D;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: var(--bg); color: var(--text); min-height: 100vh; }

    #loginView { display: flex; align-items: center; justify-content: center; min-height: 100vh; }
    .login-card { background: var(--surface); border-radius: 20px; padding: 40px; max-width: 400px; width: 100%; box-shadow: 0 4px 20px rgba(0,0,0,.08); }
    .login-card h1 { font-size: 24px; margin-bottom: 8px; }
    .login-card p { color: var(--text-muted); font-size: 14px; margin-bottom: 24px; }
    #codeInput { width: 100%; padding: 14px; border: 2px solid var(--outline); border-radius: 12px; font-size: 28px; text-align: center; letter-spacing: 8px; font-weight: 700; outline: none; margin-bottom: 16px; }
    #codeInput:focus { border-color: var(--accent); }
    .btn { background: var(--accent); color: white; padding: 14px 24px; border: none; border-radius: 12px; font-size: 15px; font-weight: 600; cursor: pointer; width: 100%; }
    .btn:hover { opacity: .9; } .btn:disabled { opacity: .5; cursor: not-allowed; }
    .error-msg { color: var(--error); font-size: 13px; margin-top: 8px; min-height: 18px; }

    #appView { display: none; }
    .app-header { background: var(--surface); padding: 0 20px; height: 56px; display: flex; align-items: center; gap: 12px; border-bottom: 1px solid var(--outline); }
    .app-header h1 { font-size: 18px; font-weight: 700; flex: 1; }
    .app-header button { background: none; border: 1px solid var(--outline); color: var(--text-muted); padding: 6px 14px; border-radius: 8px; cursor: pointer; font-size: 13px; }
    .app-body { display: flex; height: calc(100vh - 56px); }

    .sidebar { width: 220px; flex-shrink: 0; background: var(--surface); border-right: 1px solid var(--outline); display: flex; flex-direction: column; padding: 12px 0; overflow-y: auto; }
    .sidebar-item { padding: 10px 16px; cursor: pointer; font-size: 14px; border-left: 3px solid transparent; color: var(--text-muted); }
    .sidebar-item.active { border-left-color: var(--accent); color: var(--accent); background: var(--accent-light); font-weight: 600; }
    .sidebar-item:hover:not(.active) { background: var(--bg); }
    .sidebar-new-folder { margin: 8px 12px; padding: 8px; background: var(--bg); border: 1px dashed var(--outline); border-radius: 8px; cursor: pointer; font-size: 13px; color: var(--text-muted); text-align: center; }
    .sidebar-new-folder:hover { border-color: var(--accent); color: var(--accent); }

    .main-area { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
    .meeting-list { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 10px; }
    .meeting-card { background: var(--surface); border-radius: 14px; padding: 16px; cursor: pointer; border: 2px solid transparent; transition: border-color .15s; }
    .meeting-card:hover { border-color: var(--outline); }
    .meeting-card.selected { border-color: var(--accent); }
    .mc-title { font-weight: 600; font-size: 15px; margin-bottom: 6px; display: flex; align-items: center; gap: 8px; }
    .video-badge { background: #EDE9FE; color: #6D28D9; font-size: 11px; padding: 2px 7px; border-radius: 999px; font-weight: 700; }
    .mc-meta { display: flex; gap: 10px; align-items: center; font-size: 13px; color: var(--text-muted); flex-wrap: wrap; }
    .source-badge { display: inline-flex; align-items: center; padding: 3px 9px; border-radius: 999px; font-weight: 600; font-size: 11px; }
    .src-CALL { background: var(--call-bg); color: var(--call-text); }
    .src-OFFLINE_MEET { background: var(--offline-bg); color: var(--offline-text); }
    .src-ONLINE_MEET { background: var(--online-bg); color: var(--online-text); }
    .src-VOICE_MEMO { background: var(--voice-bg); color: var(--voice-text); }
    .folder-select { font-size: 12px; border: 1px solid var(--outline); border-radius: 6px; padding: 3px 6px; cursor: pointer; background: var(--bg); }
    .empty-state { text-align: center; color: var(--text-muted); font-size: 14px; padding: 60px 20px; }
    .loading { text-align: center; color: var(--text-muted); padding: 30px; font-size: 14px; }

    .detail-panel { width: 480px; flex-shrink: 0; border-left: 1px solid var(--outline); display: flex; flex-direction: column; overflow: hidden; background: var(--surface); }
    .detail-panel.hidden { display: none; }
    .detail-header { padding: 16px 20px; border-bottom: 1px solid var(--outline); }
    .detail-close { float: right; background: none; border: none; cursor: pointer; font-size: 20px; color: var(--text-muted); }
    .detail-title { font-size: 17px; font-weight: 700; margin-bottom: 6px; }
    .detail-meta { font-size: 13px; color: var(--text-muted); display: flex; gap: 10px; flex-wrap: wrap; }
    .media-wrap { padding: 12px 20px; border-bottom: 1px solid var(--outline); }
    .media-wrap video, .media-wrap audio { width: 100%; border-radius: 10px; }
    .detail-tabs { display: flex; border-bottom: 1px solid var(--outline); }
    .detail-tab { flex: 1; padding: 12px; text-align: center; font-size: 13px; font-weight: 600; cursor: pointer; color: var(--text-muted); border-bottom: 3px solid transparent; }
    .detail-tab.active { color: var(--accent); border-bottom-color: var(--accent); }
    .detail-body { flex: 1; overflow-y: auto; padding: 16px 20px; }
    .summary-text { font-size: 14px; line-height: 1.7; white-space: pre-wrap; }
    .transcript-line { padding: 10px 0; border-bottom: 1px solid var(--outline); }
    .transcript-line:last-child { border-bottom: none; }
    .t-meta { font-size: 11px; color: var(--text-muted); margin-bottom: 3px; font-weight: 600; }
    .t-speaker { color: var(--accent); }
    .t-text { font-size: 13px; line-height: 1.5; }
    .task-row { display: flex; align-items: flex-start; gap: 10px; padding: 10px 0; border-bottom: 1px solid var(--outline); }
    .task-row:last-child { border-bottom: none; }
    .task-check { width: 20px; height: 20px; border-radius: 50%; border: 2px solid var(--outline); flex-shrink: 0; display: flex; align-items: center; justify-content: center; font-size: 11px; }
    .task-check.done { background: var(--accent); border-color: var(--accent); color: white; }
    .task-title-text { font-size: 13px; font-weight: 500; }
    .task-assignee { font-size: 11px; color: var(--text-muted); }
  </style>
</head>
<body>

<div id="loginView">
  <div class="login-card">
    <h1>Ushrashuvchi</h1>
    <p>Generate a pairing code in the app (Settings → Pair with Browser), then enter it below.</p>
    <input type="text" id="codeInput" maxlength="6" placeholder="000000" inputmode="numeric" autofocus>
    <div class="error-msg" id="loginError"></div>
    <button class="btn" id="loginBtn" onclick="claimCode()">Connect</button>
  </div>
</div>

<div id="appView">
  <div class="app-header">
    <h1>Ushrashuvchi</h1>
    <button onclick="logout()">Logout</button>
  </div>
  <div class="app-body">
    <div class="sidebar">
      <div class="sidebar-item active" id="folder-all" data-folder="" onclick="selectFolder('')">All meetings</div>
      <div id="folderList"></div>
      <div class="sidebar-new-folder" onclick="createFolder()">+ New folder</div>
    </div>
    <div class="main-area">
      <div class="meeting-list" id="meetingList">
        <div class="loading">Loading meetings...</div>
      </div>
    </div>
    <div class="detail-panel hidden" id="detailPanel">
      <div class="detail-header">
        <button class="detail-close" onclick="closeDetail()">&#x2715;</button>
        <div class="detail-title" id="detailTitle"></div>
        <div class="detail-meta" id="detailMeta"></div>
      </div>
      <div class="media-wrap" id="mediaWrap"></div>
      <div class="detail-tabs" id="detailTabs">
        <div class="detail-tab active" data-pane="summary" onclick="switchPane('summary')">Summary</div>
        <div class="detail-tab" data-pane="transcript" onclick="switchPane('transcript')">Transcript</div>
        <div class="detail-tab" data-pane="tasks" onclick="switchPane('tasks')">Tasks</div>
      </div>
      <div class="detail-body" id="detailBody"></div>
    </div>
  </div>
</div>

<script>
(function() {
  'use strict';

  // All values inserted into DOM use textContent or are from trusted own API data escaped below.
  // The esc() function sanitises API text before building HTML strings.
  function esc(s) {
    return String(s || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
  function fmtDate(ms) {
    return new Date(ms).toLocaleDateString(undefined, {year:'numeric',month:'short',day:'numeric',hour:'2-digit',minute:'2-digit'});
  }
  function fmtDur(s) { return Math.floor(s/60)+'m '+(s%60)+'s'; }
  function fmtTs(ms) { const s=Math.floor(ms/1000); return Math.floor(s/60)+':'+(s%60<10?'0':'')+(s%60); }
  function srcLabel(src) {
    return {CALL:'Call',OFFLINE_MEET:'Offline',ONLINE_MEET:'Online',VOICE_MEMO:'Voice memo'}[src] || src;
  }

  let token = localStorage.getItem('ush_token');
  let meetings = [];
  let folders = [];
  let currentFolderId = '';  // '' means All
  let currentDetail = null;
  let currentPane = 'summary';

  // ── API ──
  async function api(path, opts) {
    opts = opts || {};
    const headers = Object.assign({'Authorization':'Bearer '+token,'Content-Type':'application/json'}, opts.headers||{});
    return fetch('/api/v1'+path, Object.assign({}, opts, {headers: headers}));
  }

  // ── Login ──
  window.claimCode = async function() {
    const input = document.getElementById('codeInput');
    const errEl = document.getElementById('loginError');
    const btn = document.getElementById('loginBtn');
    const code = input.value.trim();
    if (code.length !== 6) { errEl.textContent = 'Enter a 6-digit code.'; return; }
    btn.disabled = true;
    errEl.textContent = '';
    try {
      const resp = await fetch('/api/v1/auth/pair/claim', {
        method: 'POST',
        headers: {'Content-Type':'application/json'},
        body: JSON.stringify({code: code})
      });
      if (!resp.ok) { errEl.textContent = 'Code invalid or expired.'; btn.disabled=false; return; }
      const data = await resp.json();
      token = data.token;
      localStorage.setItem('ush_token', token);
      showApp();
    } catch(e) { errEl.textContent = 'Network error: '+e.message; btn.disabled=false; }
  };

  window.logout = function() {
    localStorage.removeItem('ush_token');
    token = null;
    document.getElementById('appView').style.display = 'none';
    document.getElementById('loginView').style.display = 'flex';
    document.getElementById('codeInput').value = '';
    document.getElementById('loginBtn').disabled = false;
  };

  // ── App ──
  async function showApp() {
    document.getElementById('loginView').style.display = 'none';
    document.getElementById('appView').style.display = 'block';
    await Promise.all([loadFolders(), loadMeetings()]);
  }

  async function loadFolders() {
    const resp = await api('/folders');
    if (!resp.ok) return;
    folders = await resp.json();
    renderFolderSidebar();
  }

  async function loadMeetings() {
    const resp = await api('/meetings');
    if (!resp.ok) {
      document.getElementById('meetingList').textContent = 'Failed to load meetings.';
      return;
    }
    meetings = await resp.json();
    renderMeetings();
  }

  function renderFolderSidebar() {
    const list = document.getElementById('folderList');
    list.innerHTML = '';
    folders.forEach(function(f) {
      const el = document.createElement('div');
      el.className = 'sidebar-item' + (currentFolderId === f.id ? ' active' : '');
      el.id = 'folder-' + f.id;
      el.setAttribute('data-folder', f.id);
      el.textContent = f.name;
      el.onclick = function() { selectFolder(f.id); };
      list.appendChild(el);
    });
  }

  window.selectFolder = function(folderId) {
    currentFolderId = folderId;
    document.querySelectorAll('.sidebar-item').forEach(function(el) {
      el.classList.toggle('active', el.getAttribute('data-folder') === folderId);
    });
    renderMeetings();
  };

  function renderMeetings() {
    const filtered = currentFolderId
      ? meetings.filter(function(m) { return m.folderId === currentFolderId; })
      : meetings;

    const list = document.getElementById('meetingList');
    list.innerHTML = '';

    if (filtered.length === 0) {
      const empty = document.createElement('div');
      empty.className = 'empty-state';
      empty.textContent = 'No meetings here yet.';
      list.appendChild(empty);
      return;
    }

    filtered.forEach(function(m) {
      const card = document.createElement('div');
      card.className = 'meeting-card' + (currentDetail && currentDetail.id === m.id ? ' selected' : '');
      card.id = 'mc-' + m.id;

      // Title row
      const titleRow = document.createElement('div');
      titleRow.className = 'mc-title';
      const titleSpan = document.createElement('span');
      titleSpan.textContent = m.title || 'Untitled';
      titleRow.appendChild(titleSpan);
      if (m.hasVideo) {
        const badge = document.createElement('span');
        badge.className = 'video-badge';
        badge.textContent = 'VIDEO';
        titleRow.appendChild(badge);
      }

      // Meta row
      const metaRow = document.createElement('div');
      metaRow.className = 'mc-meta';

      const srcBadge = document.createElement('span');
      srcBadge.className = 'source-badge src-' + m.audioSource;
      srcBadge.textContent = srcLabel(m.audioSource);

      const dateSpan = document.createElement('span');
      dateSpan.textContent = fmtDate(m.date);

      const durSpan = document.createElement('span');
      durSpan.textContent = fmtDur(m.durationSeconds);

      const folderSelect = document.createElement('select');
      folderSelect.className = 'folder-select';
      const noFolder = document.createElement('option');
      noFolder.value = '';
      noFolder.textContent = 'No folder';
      folderSelect.appendChild(noFolder);
      folders.forEach(function(f) {
        const opt = document.createElement('option');
        opt.value = f.id;
        opt.textContent = f.name;
        if (m.folderId === f.id) opt.selected = true;
        folderSelect.appendChild(opt);
      });
      folderSelect.onclick = function(e) { e.stopPropagation(); };
      folderSelect.onchange = function() { moveToFolder(m.id, folderSelect.value); };

      metaRow.appendChild(srcBadge);
      metaRow.appendChild(dateSpan);
      metaRow.appendChild(durSpan);
      metaRow.appendChild(folderSelect);

      card.appendChild(titleRow);
      card.appendChild(metaRow);
      card.onclick = function() { openDetail(m.id); };
      list.appendChild(card);
    });
  }

  window.moveToFolder = async function(meetingId, folderId) {
    await api('/meetings/'+meetingId, {
      method: 'PATCH',
      body: JSON.stringify({folderId: folderId || null})
    });
    const m = meetings.find(function(x){ return x.id===meetingId; });
    if (m) m.folderId = folderId || null;
    renderMeetings();
  };

  window.createFolder = async function() {
    const name = prompt('Folder name:');
    if (!name || !name.trim()) return;
    const resp = await api('/folders', {method:'POST', body: JSON.stringify({name: name.trim()})});
    if (resp.ok) { await loadFolders(); renderMeetings(); }
  };

  // ── Detail ──
  window.openDetail = async function(meetingId) {
    document.getElementById('detailPanel').classList.remove('hidden');
    document.getElementById('detailBody').textContent = 'Loading...';
    document.getElementById('mediaWrap').innerHTML = '';
    document.getElementById('detailTitle').textContent = '';
    document.getElementById('detailMeta').innerHTML = '';

    const resp = await api('/meetings/'+meetingId);
    if (!resp.ok) {
      document.getElementById('detailBody').textContent = 'Error loading meeting.';
      return;
    }
    currentDetail = await resp.json();

    document.querySelectorAll('.meeting-card').forEach(function(el){ el.classList.remove('selected'); });
    const card = document.getElementById('mc-'+meetingId);
    if (card) card.classList.add('selected');

    document.getElementById('detailTitle').textContent = currentDetail.title || 'Untitled';

    // Meta
    const metaEl = document.getElementById('detailMeta');
    metaEl.innerHTML = '';
    const sb = document.createElement('span');
    sb.className = 'source-badge src-' + currentDetail.audioSource;
    sb.textContent = srcLabel(currentDetail.audioSource);
    const ds = document.createElement('span');
    ds.textContent = fmtDate(currentDetail.date);
    const dd = document.createElement('span');
    dd.textContent = fmtDur(currentDetail.durationSeconds);
    metaEl.appendChild(sb);
    metaEl.appendChild(ds);
    metaEl.appendChild(dd);

    // Media — src URLs use own-domain API paths; token is a JWT (alphanumeric + dots/dashes, safe in URL)
    const mediaWrap = document.getElementById('mediaWrap');
    mediaWrap.innerHTML = '';
    if (currentDetail.hasVideo) {
      const vid = document.createElement('video');
      vid.controls = true;
      vid.preload = 'metadata';
      vid.src = '/api/v1/meetings/'+meetingId+'/video?token='+encodeURIComponent(token);
      mediaWrap.appendChild(vid);
    } else if (currentDetail.hasAudio) {
      const aud = document.createElement('audio');
      aud.controls = true;
      aud.preload = 'metadata';
      aud.src = '/api/v1/meetings/'+meetingId+'/audio?token='+encodeURIComponent(token);
      mediaWrap.appendChild(aud);
    }

    switchPane(currentPane);
  };

  window.closeDetail = function() {
    document.getElementById('detailPanel').classList.add('hidden');
    document.querySelectorAll('.meeting-card').forEach(function(el){ el.classList.remove('selected'); });
    currentDetail = null;
  };

  window.switchPane = function(name) {
    currentPane = name;
    document.querySelectorAll('.detail-tab').forEach(function(t){
      t.classList.toggle('active', t.getAttribute('data-pane') === name);
    });
    if (!currentDetail) return;
    const body = document.getElementById('detailBody');
    body.innerHTML = '';

    if (name === 'summary') {
      const div = document.createElement('div');
      div.className = 'summary-text';
      div.textContent = currentDetail.summary || 'No summary available.';
      body.appendChild(div);

    } else if (name === 'transcript') {
      const lines = currentDetail.transcript || [];
      if (!lines.length) { body.textContent = 'No transcript.'; return; }
      lines.forEach(function(l) {
        const row = document.createElement('div');
        row.className = 'transcript-line';
        const meta = document.createElement('div');
        meta.className = 't-meta';
        const spk = document.createElement('span');
        spk.className = 't-speaker';
        spk.textContent = l.speaker;
        const ts = document.createElement('span');
        ts.textContent = '  ' + fmtTs(l.tsStartMs);
        meta.appendChild(spk);
        meta.appendChild(ts);
        const txt = document.createElement('div');
        txt.className = 't-text';
        txt.textContent = l.text;
        row.appendChild(meta);
        row.appendChild(txt);
        body.appendChild(row);
      });

    } else if (name === 'tasks') {
      const tasks = currentDetail.tasks || [];
      if (!tasks.length) { body.textContent = 'No tasks.'; return; }
      tasks.forEach(function(t) {
        const row = document.createElement('div');
        row.className = 'task-row';
        const chk = document.createElement('div');
        chk.className = 'task-check' + (t.isCompleted ? ' done' : '');
        chk.textContent = t.isCompleted ? '✓' : '';
        const info = document.createElement('div');
        const tt = document.createElement('div');
        tt.className = 'task-title-text';
        tt.textContent = t.title;
        info.appendChild(tt);
        if (t.assignee && t.assignee !== 'Unassigned') {
          const asgn = document.createElement('div');
          asgn.className = 'task-assignee';
          asgn.textContent = '@' + t.assignee;
          info.appendChild(asgn);
        }
        row.appendChild(chk);
        row.appendChild(info);
        body.appendChild(row);
      });
    }
  };

  // ── Bootstrap ──
  document.getElementById('codeInput').addEventListener('keydown', function(e) {
    if (e.key === 'Enter') window.claimCode();
  });

  if (token) showApp();
})();
</script>
</body>
</html>
""".trimIndent()
