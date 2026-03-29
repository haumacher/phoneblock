/**
 * Generic harvest infrastructure for content scripts.
 *
 * Manages the harvest loop, state persistence, and message handling.
 * Provider-specific content scripts register their harvest function
 * via initHarvester(fn).
 */

let running = false;
let timerId = null;
let harvestFn = null;

// --- Storage helpers ---

async function loadState() {
  const data = await chrome.storage.local.get(['collected', 'requestCount', 'running']);
  return {
    collected: data.collected || {},
    requestCount: data.requestCount || 0,
    running: data.running || false
  };
}

async function saveState(state) {
  await chrome.storage.local.set(state);
}

// --- Harvest result helpers ---

/**
 * Records a harvested email in the collected map.
 * Returns {collected, requestCount, email, type, isNew} or {error}.
 */
function recordEmail(collected, requestCount, rawEmail, type, domain) {
  const email = normalizeEmail(rawEmail);
  const isNew = !collected[email];

  if (isNew) {
    collected[email] = {
      email: email,
      originalEmail: rawEmail !== email ? rawEmail : undefined,
      type: type || 'unknown',
      domain: domain,
      firstSeen: new Date().toISOString()
    };
  }

  return { collected, requestCount, email, type, isNew };
}

// --- Harvest loop ---

async function harvestLoop() {
  if (!running) return;

  const state = await loadState();
  const result = await harvestFn(state.collected, state.requestCount);
  await saveState({ collected: result.collected, requestCount: result.requestCount });

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

/**
 * Initializes the harvester with a provider-specific harvest function.
 *
 * @param {function(Object, number): Promise<Object>} fn
 *   Async function(collected, requestCount) that performs one harvest
 *   and returns {collected, requestCount, email?, type?, isNew?, error?}.
 */
function initHarvester(fn) {
  harvestFn = fn;

  // Resume if was running before page reload.
  loadState().then(state => {
    if (state.running) {
      running = true;
      harvestLoop();
    }
  });
}
