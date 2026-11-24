// Checks every password input in the web UI (main/web/index.html, #792)
// opts out of saved-password autofill. Run by `make test` when node is on PATH.
'use strict';
const fs = require('fs');
const path = require('path');
const html = fs.readFileSync(path.join(__dirname, '../main/web/index.html'), 'utf8');
const inputs = [...html.matchAll(/<input\b[^>]*>/gi)].map(match => match[0]);
const passwords = inputs.filter(input => /\btype\s*=\s*(["'])password\1/i.test(input));

if (passwords.length < 4) {
  throw new Error('expected at least 4 password inputs in index.html, found ' + passwords.length);
}

let fail = 0;
passwords.forEach((input, i) => {
  if (!/\bautocomplete\s*=\s*(["'])new-password\1/i.test(input)) {
    fail++;
    console.error('FAIL password input ' + (i + 1) + ' lacks autocomplete="new-password": ' + input);
  }
});

if (fail) { console.error('password_autofill_js_check: ' + fail + ' failure(s)'); process.exit(1); }
console.log('password_autofill_js_check: all checks passed');
