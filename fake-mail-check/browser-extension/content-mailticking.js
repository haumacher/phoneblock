/**
 * Content script for mailticking.com — harvests disposable email addresses
 * via the /get-mailbox API (used by the "Change" button in the activation dialog).
 *
 * Mailbox types (selected via checkboxes):
 *   1 = Gmail dot-trick (a.b.c@gmail.com)
 *   2 = Gmail plus-addressing (abc+d@gmail.com)
 *   3 = Googlemail (abc@googlemail.com)
 *   4 = Custom domain (abc@domain.com)
 */

/** Request all types to maximize variety. */
const TYPES = ['1', '2', '3', '4'];

initHarvester(async function(collected, requestCount) {
  try {
    const response = await fetch('/get-mailbox', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ types: TYPES })
    });

    requestCount++;

    if (!response.ok) {
      return { collected, requestCount, error: 'HTTP ' + response.status };
    }

    const data = await response.json();

    if (data.success && data.email) {
      const rawEmail = data.email.toLowerCase();
      const domain = rawEmail.substring(rawEmail.indexOf('@') + 1);
      const type = (domain.includes('gmail') || domain.includes('googlemail')) ? 'gmail' : 'domain';
      return recordEmail(collected, requestCount, rawEmail, type, domain);
    } else {
      return { collected, requestCount, error: data.error || 'Unexpected response' };
    }
  } catch (e) {
    return { collected, requestCount, error: e.message };
  }
});
