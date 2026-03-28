/**
 * Content script running on 22.do.
 *
 * Handles harvest commands from the popup by calling the site's own
 * /action/mailbox/create endpoint (same-origin, with all cookies).
 */

let running = false;
let timerId = null;

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.action === 'start') {
    running = true;
    harvest(sendResponse);
    // Return true to indicate async sendResponse.
    return true;
  }

  if (msg.action === 'stop') {
    running = false;
    if (timerId) {
      clearTimeout(timerId);
      timerId = null;
    }
    sendResponse({ stopped: true });
  }

  if (msg.action === 'ping') {
    sendResponse({ alive: true });
  }
});

async function harvest(sendResponse) {
  if (!running) {
    sendResponse({ done: true });
    return;
  }

  try {
    const response = await fetch('/action/mailbox/create', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ type: 'random' })
    });

    if (!response.ok) {
      sendResponse({ error: 'HTTP ' + response.status });
      return;
    }

    const data = await response.json();

    if (data.status && data.data) {
      const email = data.data.account + '@' + data.data.domain;
      sendResponse({
        email: email,
        type: data.data.type || 'unknown',
        domain: data.data.domain
      });
    } else {
      sendResponse({ error: 'Unexpected response', raw: JSON.stringify(data) });
    }
  } catch (e) {
    sendResponse({ error: e.message });
  }
}
