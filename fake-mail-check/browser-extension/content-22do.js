/**
 * Content script for 22.do — harvests disposable email addresses
 * via the /action/mailbox/create API.
 */

initHarvester(async function(collected, requestCount) {
  try {
    const response = await fetch('/action/mailbox/create', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ type: 'random' })
    });

    requestCount++;

    if (!response.ok) {
      return { collected, requestCount, error: 'HTTP ' + response.status };
    }

    const data = await response.json();

    if (data.status && data.data) {
      const rawEmail = (data.data.account + '@' + data.data.domain).toLowerCase();
      return recordEmail(collected, requestCount, rawEmail, data.data.type, data.data.domain);
    } else {
      return { collected, requestCount, error: 'Unexpected response' };
    }
  } catch (e) {
    return { collected, requestCount, error: e.message };
  }
});
