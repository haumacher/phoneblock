/**
 * Content script running on 22.do.
 *
 * Owns the harvest loop and persists all state in chrome.storage.local
 * so that data survives popup close/reopen and browser backgrounding.
 */

let running = false;
let timerId = null;

// --- Storage helpers ---

async function loadState() {
  const data = await chrome.storage.local.get(['collected', 'requestCount', 'running']);
  return {
    collected: data.collected || {},  // email -> {email, type, domain, firstSeen}
    requestCount: data.requestCount || 0,
    running: data.running || false
  };
}

async function saveState(state) {
  await chrome.storage.local.set(state);
}

// --- Harvest logic ---

async function harvestOne(collected, requestCount) {
  try {
    const response = await fetch('/action/mailbox/create', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ type: 'random' })
    });

    requestCount++;

    if (!response.ok) {
      await saveState({ collected, requestCount });
      return { collected, requestCount, error: 'HTTP ' + response.status };
    }

    const data = await response.json();

    if (data.status && data.data) {
      const rawEmail = (data.data.account + '@' + data.data.domain).toLowerCase();
      const email = normalizeEmail(rawEmail);
      const isNew = !collected[email];

      if (isNew) {
        collected[email] = {
          email: email,
          originalEmail: rawEmail !== email ? rawEmail : undefined,
          type: data.data.type || 'unknown',
          domain: data.data.domain,
          firstSeen: new Date().toISOString()
        };
      }

      await saveState({ collected, requestCount });
      return { collected, requestCount, email, type: data.data.type, isNew };
    } else {
      await saveState({ collected, requestCount });
      return { collected, requestCount, error: 'Unexpected response' };
    }
  } catch (e) {
    await saveState({ collected, requestCount });
    return { collected, requestCount, error: e.message };
  }
}

async function harvestLoop() {
  if (!running) return;

  const state = await loadState();
  const result = await harvestOne(state.collected, state.requestCount);

  // Re-check after async work — stopHarvest may have been called during fetch.
  if (!running) return;

  const delay = result.error ? 10000 : 2000 + Math.random() * 3000;
  timerId = setTimeout(harvestLoop, delay);
}

async function startHarvest() {
  if (running) return;
  running = true;
  await saveState({ ...(await loadState()), running: true });
  harvestLoop();
}

async function stopHarvest() {
  running = false;
  if (timerId) {
    clearTimeout(timerId);
    timerId = null;
  }
  await saveState({ ...(await loadState()), running: false });
}

// --- Message handling ---

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.action === 'start') {
    startHarvest().then(() => sendResponse({ started: true }));
    return true;
  }

  if (msg.action === 'stop') {
    stopHarvest().then(() => sendResponse({ stopped: true }));
    return true;
  }

  if (msg.action === 'getState') {
    loadState().then(state => sendResponse(state));
    return true;
  }

  if (msg.action === 'clear') {
    stopHarvest().then(() =>
      saveState({ collected: {}, requestCount: 0, running: false })
    ).then(() => sendResponse({ cleared: true }));
    return true;
  }

  if (msg.action === 'ping') {
    sendResponse({ alive: true });
  }
});

// Resume harvest if it was running before page reload.
loadState().then(state => {
  if (state.running) {
    running = true;
    harvestLoop();
  }
});
