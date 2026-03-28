/**
 * Popup script for the Fake Mail Harvester extension.
 *
 * Pure viewer — all state lives in chrome.storage.local,
 * managed by the content script.
 */

const startBtn = document.getElementById('startBtn');
const stopBtn = document.getElementById('stopBtn');
const downloadBtn = document.getElementById('downloadBtn');
const clearBtn = document.getElementById('clearBtn');
const statusEl = document.getElementById('status');
const logEl = document.getElementById('log');

const requestCountEl = document.getElementById('requestCount');
const emailCountEl = document.getElementById('emailCount');
const gmailCountEl = document.getElementById('gmailCount');
const msCountEl = document.getElementById('msCount');
const domainCountEl = document.getElementById('domainCount');

startBtn.addEventListener('click', start);
stopBtn.addEventListener('click', stop);
downloadBtn.addEventListener('click', download);
clearBtn.addEventListener('click', clear);

// Listen for storage changes — reliable live updates without message routing.
let lastEmailCount = 0;
chrome.storage.onChanged.addListener((changes, area) => {
  if (area !== 'local') return;

  if (changes.requestCount) {
    requestCountEl.textContent = changes.requestCount.newValue || 0;
  }

  if (changes.collected) {
    const collected = changes.collected.newValue || {};
    updateCountsFromCollected(collected);

    // Log new entries by comparing counts.
    const entries = Object.values(collected);
    const newCount = entries.length;
    if (newCount > lastEmailCount) {
      // Show the newest entries (sorted by firstSeen desc).
      const sorted = entries.sort((a, b) => b.firstSeen.localeCompare(a.firstSeen));
      const added = sorted.slice(0, newCount - lastEmailCount);
      for (const e of added.reverse()) {
        addLog(e.email, 'log-' + e.type);
      }
    }
    lastEmailCount = newCount;
  }

  if (changes.running) {
    setRunningUI(changes.running.newValue);
  }
});

async function getTab() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  return tab;
}

async function ensureContentScript(tab) {
  if (!tab || !tab.url || !tab.url.includes('22.do')) {
    statusEl.innerHTML = '<span class="error">Bitte zuerst 22.do im Browser öffnen.</span>';
    return false;
  }
  try {
    await chrome.tabs.sendMessage(tab.id, { action: 'ping' });
    return true;
  } catch (e) {
    statusEl.innerHTML = '<span class="error">Content script nicht erreichbar. Seite neu laden?</span>';
    return false;
  }
}

async function start() {
  const tab = await getTab();
  if (!await ensureContentScript(tab)) return;

  await chrome.tabs.sendMessage(tab.id, { action: 'start' });
  setRunningUI(true);
}

async function stop() {
  const tab = await getTab();
  if (tab) {
    try {
      await chrome.tabs.sendMessage(tab.id, { action: 'stop' });
    } catch (e) { /* ignore */ }
  }
  setRunningUI(false);
}

async function clear() {
  const tab = await getTab();
  if (tab) {
    try {
      await chrome.tabs.sendMessage(tab.id, { action: 'clear' });
    } catch (e) {
      // Fallback: clear storage directly.
      await chrome.storage.local.clear();
    }
  }
  requestCountEl.textContent = '0';
  emailCountEl.textContent = '0';
  gmailCountEl.textContent = '0';
  msCountEl.textContent = '0';
  domainCountEl.textContent = '0';
  logEl.innerHTML = '';
  downloadBtn.disabled = true;
  setRunningUI(false);
  statusEl.textContent = 'Daten gelöscht.';
}

function setRunningUI(isRunning) {
  startBtn.disabled = isRunning;
  stopBtn.disabled = !isRunning;
  statusEl.textContent = isRunning ? 'Harvesting...' : 'Gestoppt.';
}

function updateCountsFromCollected(collected) {
  if (!collected) return;
  const entries = Object.values(collected);
  emailCountEl.textContent = entries.length;
  downloadBtn.disabled = entries.length === 0;

  let gmail = 0, ms = 0;
  const domains = new Set();
  for (const e of entries) {
    if (e.type === 'gmail') gmail++;
    else if (e.type === 'microsoft') ms++;
    domains.add(e.domain);
  }
  gmailCountEl.textContent = gmail;
  msCountEl.textContent = ms;
  domainCountEl.textContent = domains.size;
}

function addLog(text, cssClass) {
  const div = document.createElement('div');
  div.textContent = text;
  if (cssClass) div.className = cssClass;
  logEl.prepend(div);
  while (logEl.children.length > 200) {
    logEl.removeChild(logEl.lastChild);
  }
}

async function download() {
  const data = await chrome.storage.local.get('collected');
  const collected = data.collected || {};
  const entries = Object.values(collected).map(e => {
    const entry = { email: e.email, type: e.type, domain: e.domain, source: '22do' };
    if (e.originalEmail) entry.originalEmail = e.originalEmail;
    return entry;
  });

  const blob = new Blob([JSON.stringify(entries, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'fake-emails-22do-' + new Date().toISOString().slice(0, 10) + '.json';
  a.click();
  URL.revokeObjectURL(url);
}

// On popup open: restore UI from persisted state.
(async () => {
  const tab = await getTab();
  if (!tab || !tab.url || !tab.url.includes('22.do')) {
    statusEl.innerHTML = '<span class="error">Bitte zuerst 22.do im Browser öffnen.</span>';
    return;
  }

  try {
    const state = await chrome.tabs.sendMessage(tab.id, { action: 'getState' });
    requestCountEl.textContent = state.requestCount;
    updateCountsFromCollected(state.collected);
    setRunningUI(state.running);

    // Initialize count so storage listener only logs truly new entries.
    lastEmailCount = Object.keys(state.collected).length;

    // Show recent entries in log.
    const entries = Object.values(state.collected);
    entries.sort((a, b) => b.firstSeen.localeCompare(a.firstSeen));
    for (const e of entries.slice(0, 50)) {
      addLog(e.email, 'log-' + e.type);
    }
  } catch (e) {
    statusEl.innerHTML = '<span class="error">Content script nicht erreichbar. Seite neu laden?</span>';
  }
})();
