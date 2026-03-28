/**
 * E-mail normalization for well-known public domains.
 *
 * Port of de.haumacher.mailcheck.EmailNormalizer — applies provider-specific
 * rules (dot-stripping, plus-addressing, domain aliasing) so that aliases of
 * the same mailbox map to a single canonical address.
 */

const EMAIL_RULES = {
  // Gmail
  'gmail.com':       { canonicalDomain: 'gmail.com',    stripDots: true },
  'googlemail.com':  { canonicalDomain: 'gmail.com',    stripDots: true },

  // Outlook
  'outlook.com':     { canonicalDomain: 'outlook.com',  stripDots: false },
  'hotmail.com':     { canonicalDomain: 'outlook.com',  stripDots: false },
  'live.com':        { canonicalDomain: 'outlook.com',  stripDots: false },
  'msn.com':         { canonicalDomain: 'outlook.com',  stripDots: false },

  // Yahoo
  'yahoo.com':       { canonicalDomain: 'yahoo.com',    stripDots: false },
  'ymail.com':       { canonicalDomain: 'yahoo.com',    stripDots: false },
  'rocketmail.com':  { canonicalDomain: 'yahoo.com',    stripDots: false },

  // iCloud
  'icloud.com':      { canonicalDomain: 'icloud.com',   stripDots: false },
  'me.com':          { canonicalDomain: 'icloud.com',   stripDots: false },
  'mac.com':         { canonicalDomain: 'icloud.com',   stripDots: false },

  // Proton
  'protonmail.com':  { canonicalDomain: 'proton.me',    stripDots: false },
  'proton.me':       { canonicalDomain: 'proton.me',    stripDots: false },
  'pm.me':           { canonicalDomain: 'proton.me',    stripDots: false },
};

/**
 * Normalizes an e-mail address using provider-specific rules.
 *
 * @param {string} email Raw e-mail address.
 * @returns {string} Normalized address. For known public domains, aliases
 *   are collapsed (dots stripped for Gmail, plus-tags removed, domain
 *   aliases unified). For unknown domains, returns the lowercase address.
 */
function normalizeEmail(email) {
  if (!email) return email;

  const atIndex = email.indexOf('@');
  if (atIndex < 0) return email.toLowerCase();

  let localPart = email.substring(0, atIndex);
  const domain = email.substring(atIndex + 1).toLowerCase();

  const rule = EMAIL_RULES[domain];
  if (!rule) {
    // Unknown domain — just lowercase.
    return localPart.toLowerCase() + '@' + domain;
  }

  // Strip plus-addressing suffix.
  const plusIndex = localPart.indexOf('+');
  if (plusIndex >= 0) {
    localPart = localPart.substring(0, plusIndex);
  }

  // Strip dots if applicable (Gmail).
  if (rule.stripDots) {
    localPart = localPart.replaceAll('.', '');
  }

  return localPart.toLowerCase() + '@' + rule.canonicalDomain;
}
