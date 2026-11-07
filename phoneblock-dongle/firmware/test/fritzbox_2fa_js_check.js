// Checks that the Fritz!Box setup wizard (submitFritzbox in
// main/web/index.html) goes on to poll the 2FA state when the box asks for
// a second factor (#722). A stale reference to the old submit event made the
// 2FA branch throw, so the dongle started the handshake but the UI never
// asked it to finish. Run by `make test` when node is on PATH.
'use strict';
const fs = require('fs');
const path = require('path');
const html = fs.readFileSync(path.join(__dirname, '../main/web/index.html'), 'utf8');
const m = html.match(/\nasync function submitFritzbox\(form, onConflict\)\{[\s\S]*?\n\}\n/);
if (!m) throw new Error('no submitFritzbox() in index.html');

// Runs submitFritzbox against a stubbed DOM and a canned answer of
// POST api/fritzbox-setup; returns what the wizard did.
async function run(answer){
  const seen = {polled: false, conflict: false, posted: null};
  const msg = {style: {}, textContent: '', innerHTML: ''};
  const conflictEl = {style: {}, innerHTML: ''};
  const btn = {disabled: false};
  const pass = {value: 'secret'};
  const form = {querySelector: s =>
    s === 'button' ? btn : s === 'input[name=fritz_pass]' ? pass : null};
  const stubs = {
    $: id => id === 'fbMsg' ? msg : id === 'fbConflict' ? conflictEl : null,
    t: k => k,
    esc: s => String(s),
    render2FAInstructions: () => '',
    postForm: async (url, f, extra) => { seen.posted = {url, extra}; return answer; },
    poll2FA: async () => { seen.polled = true; },
    renderFritzboxConflict: () => { seen.conflict = true; },
    setTimeout: () => {},
    location: {},
  };
  const submit = new Function(...Object.keys(stubs),
    m[0] + 'return submitFritzbox;')(...Object.values(stubs));
  await submit(form, '');
  return {...seen, pass: pass.value, btn: btn.disabled};
}

(async () => {
  let fail = 0;
  const check = (ok, what) => { if (!ok){ fail++; console.error('FAIL ' + what); } };

  let r;
  try {
    r = await run({ok: false, data: {two_factor: true, methods: 'button,dtmf;*1234'}});
  } catch (e) {
    fail++; console.error('FAIL the 2FA answer throws: ' + e.message);
  }
  if (r){
    check(r.polled, 'a 2FA answer starts polling the 2FA state');
    check(r.pass === '', 'a 2FA answer clears the admin password field');
  }

  r = await run({ok: false, data: {conflict: true, entries: []}});
  check(!r.polled && r.conflict, 'a conflict answer asks the user, no 2FA poll');

  r = await run({ok: true, data: {internal_number: '620'}});
  check(!r.polled && !r.conflict, 'a success needs no 2FA poll');
  check(r.posted && r.posted.url === 'api/fritzbox-setup' && r.posted.extra === null,
    'the first attempt posts without on_conflict');

  if (fail){ console.error('fritzbox_2fa_js_check: ' + fail + ' failure(s)'); process.exit(1); }
  console.log('fritzbox_2fa_js_check: all checks passed');
})();
