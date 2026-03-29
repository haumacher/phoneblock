/**
 * Content script for temp-mail.org — harvests disposable email addresses
 * by clicking "Delete" to cycle through randomly generated addresses.
 *
 * This provider uses its own disposable domains (not Gmail/Outlook).
 * The email address appears in the #mail input field.
 */

initHarvester(async function(collected, requestCount) {
  try {
    // Wait for email to be loaded (not "Loading..").
    const email = await waitForEmail();
    if (!email) {
      return { collected, requestCount, error: 'Timeout waiting for email' };
    }

    requestCount++;
    const rawEmail = email.toLowerCase();
    const domain = rawEmail.substring(rawEmail.indexOf('@') + 1);
    const result = recordEmail(collected, requestCount, rawEmail, 'domain', domain);

    // Click "Delete" to generate a new address for the next iteration.
    const deleteBtn = Array.from(document.querySelectorAll('button'))
      .find(b => b.textContent.trim() === 'Delete');
    if (deleteBtn) {
      deleteBtn.click();
    }

    return result;
  } catch (e) {
    return { collected, requestCount, error: e.message };
  }
});

/**
 * Waits for the #mail input to contain a valid email address.
 */
function waitForEmail() {
  return new Promise((resolve) => {
    let elapsed = 0;
    const interval = setInterval(() => {
      const input = document.getElementById('mail');
      if (input && input.value && input.value.includes('@') && !input.value.includes('Loading')) {
        clearInterval(interval);
        resolve(input.value);
      }
      elapsed += 300;
      if (elapsed > 15000) {
        clearInterval(interval);
        resolve(null);
      }
    }, 300);
  });
}
