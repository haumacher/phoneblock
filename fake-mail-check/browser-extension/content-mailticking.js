/**
 * Content script for mailticking.com — harvests disposable email addresses
 * by cycling through the /change-mailbox API.
 *
 * Mailbox types:
 *   1 = Gmail dot-trick (a.b.c@gmail.com)
 *   2 = Gmail plus-addressing (abc+smith@gmail.com)
 *   3 = Googlemail (a.bc@googlemail.com)
 *   4 = Custom domain (requires domain parameter)
 */

/** Mailbox types to cycle through (free tier, no VIP). */
const TYPES = [1, 2, 3];
let typeIndex = 0;

/**
 * Reads the current email address and auth code from the page.
 */
function readCurrentMail() {
  const input = document.getElementById('active-mail');
  if (!input) return null;
  const email = input.value;
  const code = input.getAttribute('data-code');
  if (!email || !code || email === 'Loading...') return null;
  return { email, code };
}

initHarvester(async function(collected, requestCount) {
  try {
    // Read current address from the page (it's already a fake address).
    const current = readCurrentMail();
    if (!current) {
      return { collected, requestCount, error: 'Page not ready (no active mail)' };
    }

    // Record the current address before changing.
    const rawEmail = current.email.toLowerCase();
    const domain = rawEmail.substring(rawEmail.indexOf('@') + 1);
    const type = domain.includes('gmail') || domain.includes('googlemail') ? 'gmail' : 'domain';
    const result = recordEmail(collected, requestCount + 1, rawEmail, type, domain);

    // Request a new address by cycling through types.
    const nextType = TYPES[typeIndex % TYPES.length];
    typeIndex++;

    const response = await fetch('/change-mailbox', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        oldMail: current.email,
        code: current.code,
        type: nextType
      }),
      redirect: 'manual'
    });

    // The server responds with a redirect to / which reloads the page.
    // We need to fetch the new page to get the new address.
    if (response.type === 'opaqueredirect' || response.ok || response.status === 302) {
      // Fetch the main page to get the new address.
      const pageResponse = await fetch('/', { credentials: 'same-origin' });
      const html = await pageResponse.text();

      // Extract new email and code from the HTML.
      const match = html.match(/id="active-mail"[^>]*value="([^"]*)"[^>]*data-code="([^"]*)"/);
      if (match) {
        // Update the DOM so the next iteration reads the new address.
        const input = document.getElementById('active-mail');
        if (input) {
          input.value = match[1];
          input.setAttribute('data-code', match[2]);
        }
      }
    }

    result.requestCount = requestCount + 1;
    return result;
  } catch (e) {
    return { collected, requestCount, error: e.message };
  }
});
