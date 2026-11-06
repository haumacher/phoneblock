// Checks that the web UI shows its sign-in screen only for the dongle's own
// login gate (main/web/index.html, the <login-gate-lib> block), not for the
// 401 Home Assistant's Supervisor answers while an ingress session has
// expired (#703). Run by `make test` when node is on PATH.
'use strict';
const fs = require('fs');
const path = require('path');
const html = fs.readFileSync(path.join(__dirname, '../main/web/index.html'), 'utf8');
const m = html.match(/<login-gate-lib>([\s\S]*?)\/\/ <\/login-gate-lib>/);
if (!m) throw new Error('no <login-gate-lib> block in index.html');

const lib = new Function(m[1] + 'return {isLoginRequired};')();

const resp = (status, body, type) =>
  new Response(body, {status, headers: {'Content-Type': type}});

(async () => {
  let fail = 0;
  const check = async (p, want, what) => {
    if (await lib.isLoginRequired(p) !== want){ fail++; console.error('FAIL ' + what); }
  };
  // The body web_auth.c sends with its 401.
  await check(resp(401, '{"ok":false,"reason":"login_required"}', 'application/json'),
    true, 'the dongle gate asks for a sign-in');
  // aiohttp's default 401 from the Supervisor's ingress proxy.
  await check(resp(401, '401: Unauthorized', 'text/plain; charset=utf-8'),
    false, 'an expired ingress session is no sign-in request');
  await check(resp(401, '', 'text/plain'), false, 'an empty 401 is no sign-in request');
  await check(resp(401, '{"ok":false}', 'application/json'),
    false, 'a 401 without the reason is no sign-in request');
  await check(resp(200, '{"reason":"login_required"}', 'application/json'),
    false, 'only a 401 counts');
  await check(null, false, 'no response (network error)');

  if (fail){ console.error('login_gate_js_check: ' + fail + ' failure(s)'); process.exit(1); }
  console.log('login_gate_js_check: all checks passed');
})();
