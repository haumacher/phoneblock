/**
 * Content script for smailpro.com — harvests disposable Gmail/Outlook addresses
 * by clicking the Create/Generate buttons and reading the result from the sidebar.
 *
 * Note: Free tier has a limit of 3 addresses per session.
 */

/** Set of already-seen addresses to detect when a new one appears. */
const seen = new Set();

/**
 * Reads all email addresses currently shown in the sidebar list.
 */
function readSidebarEmails() {
  const items = document.querySelectorAll('[x-data="create()"] li');
  const emails = [];
  for (const item of items) {
    // The address is in nested divs inside each list item.
    const text = item.textContent;
    const match = text.match(/([a-zA-Z0-9.+_-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})/);
    if (match) {
      emails.push(match[1].toLowerCase());
    }
  }
  return emails;
}

/**
 * Waits for a condition to become true, checking every interval ms.
 */
function waitFor(conditionFn, timeoutMs = 15000, intervalMs = 500) {
  return new Promise((resolve, reject) => {
    const start = Date.now();
    const check = () => {
      const result = conditionFn();
      if (result) {
        resolve(result);
      } else if (Date.now() - start > timeoutMs) {
        reject(new Error('Timeout waiting for condition'));
      } else {
        setTimeout(check, intervalMs);
      }
    };
    check();
  });
}

/**
 * Deletes the newest (first) email in the sidebar to free up a slot.
 */
async function deleteNewestEmail() {
  const deleteBtn = Array.from(document.querySelectorAll('button'))
    .find(b => b.textContent.trim().includes('Delete'));

  if (deleteBtn) {
    deleteBtn.click();
    // Wait a bit for the deletion to complete.
    await new Promise(r => setTimeout(r, 1000));
  }
}

initHarvester(async function(collected, requestCount) {
  try {
    // Seed seen set on first run.
    if (seen.size === 0) {
      for (const email of readSidebarEmails()) {
        seen.add(email);
      }
    }

    // Click "Create" button to open the dialog.
    const createBtn = document.querySelector('button img[src*="create"], button img[src*="add"]');
    const createButton = createBtn ? createBtn.closest('button') :
      Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('Create'));

    if (!createButton) {
      return { collected, requestCount, error: 'Create button not found' };
    }
    createButton.click();

    // Wait for the Generate button to appear in the dialog.
    await waitFor(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      return btns.find(b => b.textContent.trim().includes('Generate'));
    }, 5000);

    // Small delay for dialog animation.
    await new Promise(r => setTimeout(r, 500));

    // Click "Generate".
    const generateBtn = Array.from(document.querySelectorAll('button'))
      .find(b => b.textContent.trim().includes('Generate'));

    if (!generateBtn) {
      return { collected, requestCount, error: 'Generate button not found' };
    }
    generateBtn.click();

    requestCount++;

    // Wait for a new email to appear in the sidebar.
    const newEmail = await waitFor(() => {
      const emails = readSidebarEmails();
      return emails.find(e => !seen.has(e));
    }, 15000);

    seen.add(newEmail);
    const domain = newEmail.substring(newEmail.indexOf('@') + 1);
    const type = (domain.includes('gmail') || domain.includes('googlemail')) ? 'gmail' :
                 (domain.includes('outlook') || domain.includes('hotmail')) ? 'microsoft' : 'domain';

    const result = recordEmail(collected, requestCount, newEmail, type, domain);

    // Delete the address to free up a slot (free tier: max 3 active).
    await deleteNewestEmail();

    return result;
  } catch (e) {
    return { collected, requestCount, error: e.message };
  }
});
