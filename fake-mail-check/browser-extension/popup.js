/**
 * Popup script for the Fake Mail Harvester extension.
 *
 * Sends harvest commands to the content script on 22.do and
 * collects the results for JSON download.
 */

const collected = new Map(); // email -> {email, type, domain, firstSeen}
let running = false;
let requestCount = 0;

const startBtn = document.getElementById('startBtn');
const stopBtn = document.getElementById('stopBtn');
const downloadBtn = document.getElementById('downloadBtn');
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

async function getTab() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  return tab;
}

async function start() {
  const tab = await getTab();
  if (!tab || !tab.url || !tab.url.includes('22.do')) {
    statusEl.innerHTML = '<span class="error">Bitte zuerst 22.do im Browser öffnen.</span>';
    return;
  }

  // Check content script is reachable.
  try {
    await chrome.tabs.sendMessage(tab.id, { action: 'ping' });
  } catch (e) {
    statusEl.innerHTML = '<span class="error">Content script nicht erreichbar. Seite neu laden?</span>';
    return;
  }

  running = true;
  startBtn.disabled = true;
  stopBtn.disabled = false;
  statusEl.textContent = 'Harvesting...';
  harvestLoop(tab.id);
}

function stop() {
  running = false;
  startBtn.disabled = false;
  stopBtn.disabled = true;
  statusEl.textContent = 'Gestoppt.';

  getTab().then(tab => {
    if (tab) {
      chrome.tabs.sendMessage(tab.id, { action: 'stop' }).catch(() => {});
    }
  });
}

async function harvestLoop(tabId) {
  if (!running) return;

  try {
    const result = await chrome.tabs.sendMessage(tabId, { action: 'start' });
    requestCount++;
    requestCountEl.textContent = requestCount;

    if (result.error) {
      addLog('Error: ' + result.error, 'error');
      // On error, wait longer before retrying.
      setTimeout(() => harvestLoop(tabId), 10000);
      return;
    }

    if (result.email) {
      const emailLower = result.email.toLowerCase();
      const isNew = !collected.has(emailLower);

      if (isNew) {
        collected.set(emailLower, {
          email: emailLower,
          type: result.type,
          domain: result.domain,
          firstSeen: new Date().toISOString()
        });
        downloadBtn.disabled = false;
        addLog(emailLower, 'log-' + result.type);
      } else {
        addLog(emailLower + ' (dup)', 'log-skip');
      }

      updateCounts();
    }
  } catch (e) {
    addLog('Send failed: ' + e.message, 'error');
  }

  if (running) {
    // Random delay 2-5 seconds.
    const delay = 2000 + Math.random() * 3000;
    setTimeout(() => harvestLoop(tabId), delay);
  }
}

function updateCounts() {
  emailCountEl.textContent = collected.size;
  let gmail = 0, ms = 0, domain = 0;
  for (const entry of collected.values()) {
    if (entry.type === 'gmail') gmail++;
    else if (entry.type === 'microsoft') ms++;
    else domain++;
  }
  gmailCountEl.textContent = gmail;
  msCountEl.textContent = ms;
  domainCountEl.textContent = domain;
}

function addLog(text, cssClass) {
  const div = document.createElement('div');
  div.textContent = text;
  if (cssClass) div.className = cssClass;
  logEl.prepend(div);

  // Keep log size manageable.
  while (logEl.children.length > 200) {
    logEl.removeChild(logEl.lastChild);
  }
}

function download() {
  const entries = Array.from(collected.values()).map(e => ({
    email: e.email,
    type: e.type,
    domain: e.domain,
    source: '22do'
  }));

  const blob = new Blob([JSON.stringify(entries, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'fake-emails-22do-' + new Date().toISOString().slice(0, 10) + '.json';
  a.click();
  URL.revokeObjectURL(url);
}
