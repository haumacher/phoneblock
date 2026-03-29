/**
 * Content script for emailnator.com — harvests disposable Gmail addresses
 * by clicking "Generate New" and reading the email from the input field.
 *
 * Supports: Domain, +Gmail, .Gmail, GoogleMail variants (via checkboxes).
 */

initHarvester(async function(collected, requestCount) {
  try {
    // Click "Generate New" button.
    const generateBtn = Array.from(document.querySelectorAll('button'))
      .find(b => b.textContent.includes('Generate New'));

    if (!generateBtn) {
      return { collected, requestCount, error: 'Generate New button not found' };
    }
    generateBtn.click();

    requestCount++;

    // Wait for the email input to update.
    const input = document.querySelector('input[placeholder="Email Address"]');
    if (!input) {
      return { collected, requestCount, error: 'Email input not found' };
    }

    const oldValue = input.value;
    await new Promise((resolve, reject) => {
      let elapsed = 0;
      const interval = setInterval(() => {
        elapsed += 200;
        if (input.value && input.value !== oldValue) {
          clearInterval(interval);
          resolve();
        } else if (elapsed > 10000) {
          clearInterval(interval);
          reject(new Error('Timeout waiting for new email'));
        }
      }, 200);
    });

    const rawEmail = input.value.toLowerCase();
    const domain = rawEmail.substring(rawEmail.indexOf('@') + 1);
    const type = (domain.includes('gmail') || domain.includes('googlemail')) ? 'gmail' : 'domain';

    return recordEmail(collected, requestCount, rawEmail, type, domain);
  } catch (e) {
    return { collected, requestCount, error: e.message };
  }
});
