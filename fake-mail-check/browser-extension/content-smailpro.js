/**
 * Content script for smailpro.com — harvests disposable Gmail/Outlook addresses
 * via the /app/create API with reCAPTCHA v3 token.
 *
 * Since grecaptcha lives in the page context (not the content script's isolated
 * world), we inject a helper script into the page that generates captcha tokens
 * and communicates them back via window.postMessage.
 */

const SITE_KEY = '6Ldd8-IUAAAAAIdqbOociFKyeBGFsp3nNUM_6_SC';

/** Query parameters to cycle through. */
const QUERIES = [
  { username: 'random', type: 'alias', domain: 'gmail.com', server: '1' },
  { username: 'random', type: 'alias', domain: 'googlemail.com', server: '1' },
  { username: 'random', type: 'alias', domain: 'outlook.com', server: '1' },
];
let queryIndex = 0;

// Inject a helper into the page context for reCAPTCHA access.
const injected = document.createElement('script');
injected.textContent = `
  window.addEventListener('message', async (event) => {
    if (event.data && event.data.type === 'smailpro-captcha-request') {
      try {
        const token = await grecaptcha.execute('${SITE_KEY}', {action: 'create'});
        window.postMessage({type: 'smailpro-captcha-response', token: token}, '*');
      } catch(e) {
        window.postMessage({type: 'smailpro-captcha-response', error: e.message}, '*');
      }
    }
  });
`;
document.documentElement.appendChild(injected);
injected.remove();

/**
 * Requests a reCAPTCHA token from the page context via postMessage.
 */
function getCaptchaToken() {
  return new Promise((resolve, reject) => {
    const handler = (event) => {
      if (event.data && event.data.type === 'smailpro-captcha-response') {
        window.removeEventListener('message', handler);
        if (event.data.error) {
          reject(new Error(event.data.error));
        } else {
          resolve(event.data.token);
        }
      }
    };
    window.addEventListener('message', handler);
    window.postMessage({type: 'smailpro-captcha-request'}, '*');

    // Timeout after 10 seconds.
    setTimeout(() => {
      window.removeEventListener('message', handler);
      reject(new Error('Captcha timeout'));
    }, 10000);
  });
}

function toQueryString(params) {
  return '?' + Object.entries(params).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&');
}

initHarvester(async function(collected, requestCount) {
  try {
    const token = await getCaptchaToken();
    const query = QUERIES[queryIndex % QUERIES.length];
    queryIndex++;

    const url = 'https://smailpro.com/app/create' + toQueryString(query);
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        'x-captcha': token
      }
    });

    requestCount++;

    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      return { collected, requestCount, error: error.msg || 'HTTP ' + response.status };
    }

    const data = await response.json();

    if (data.address) {
      const rawEmail = data.address.toLowerCase();
      const domain = rawEmail.substring(rawEmail.indexOf('@') + 1);
      const type = (domain.includes('gmail') || domain.includes('googlemail')) ? 'gmail' : 'microsoft';
      return recordEmail(collected, requestCount, rawEmail, type, domain);
    } else {
      return { collected, requestCount, error: 'No address in response' };
    }
  } catch (e) {
    return { collected, requestCount, error: e.message };
  }
});
