// Checks that the web UI shows English text, not raw keys, for keys the
// served language pack lacks (main/web/index.html, t() and loadUiPack). Right
// after an update the device serves the previous version's pack until the
// refresh arrives; keys the new version added are missing there (#721).
// Run by `make test` when node is on PATH.
'use strict';
const fs = require('fs');
const path = require('path');
const html = fs.readFileSync(path.join(__dirname, '../main/web/index.html'), 'utf8');
const m = html.match(/\nconst I18N = \{\};[\s\S]*?\n(?=\/\/ The locales the device can serve)/);
if (!m) throw new Error('no i18n block (const I18N ... loadUiPack) in index.html');

// Serves canned packs: `served` for api/i18n/ui, `base` for ?fallback=1
// (null = the request fails), and counts the requests per URL.
function load(served, base){
  const calls = {};
  const fetch = async url => {
    calls[url] = (calls[url] || 0) + 1;
    const dict = url === 'api/i18n/ui?fallback=1' ? base : served;
    if (dict === null) throw new Error('network');
    return {ok: true, json: async () => dict};
  };
  const lib = new Function('fetch', 'document',
    m[0] + 'return {t, loadUiPack, setLang: l => { LANG = l; }, I18N};')(fetch, {});
  return {lib, calls};
}

// The two keys from the report in #721: in the previous version's pack
// "duration.minutes" is present, "status.counters.reset" (new) is not.
const EN = {'@@locale': 'en', 'duration.minutes': 'min',
            'status.counters.reset': 'Reset All Counters', 'with.param': 'Hello {name}'};
const DE_OLD = {'@@locale': 'de', 'duration.minutes': 'Min.', 'with.param': 'Hallo {name}'};

(async () => {
  let fail = 0;
  const check = (ok, what) => { if (!ok){ fail++; console.error('FAIL ' + what); } };

  // German device, previous version's German pack still served.
  let {lib, calls} = load(DE_OLD, EN);
  lib.setLang('de');
  await lib.loadUiPack('de');
  check(lib.t('duration.minutes') === 'Min.', 'a key in the served pack stays German');
  check(lib.t('status.counters.reset') === 'Reset All Counters',
        'a key missing from a stale pack shows English, not the key');
  check(lib.t('with.param', {name: 'Ma'}) === 'Hallo Ma', 'parameters still apply');
  check(lib.t('no.such.key') === 'no.such.key', 'an unknown key still falls back to itself');
  check(calls['api/i18n/ui'] === 1 && calls['api/i18n/ui?fallback=1'] === 1,
        'one request each for the pack and the fallback');

  // The fallback is fetched once per page, not on every pack load.
  await lib.loadUiPack('de');
  check(calls['api/i18n/ui?fallback=1'] === 1, 'the fallback is loaded only once');

  // English device with a stale downloaded English pack: still covered.
  ({lib} = load({'@@locale': 'en', 'duration.minutes': 'min'}, EN));
  await lib.loadUiPack('en');
  check(lib.t('status.counters.reset') === 'Reset All Counters', 'a stale English pack is covered too');

  // Fallback request fails (older firmware unreachable mid-load): no throw,
  // served keys work, missing ones fall back to the key as before.
  ({lib} = load(DE_OLD, null));
  lib.setLang('de');
  await lib.loadUiPack('de');
  check(lib.t('duration.minutes') === 'Min.', 'served keys work without the fallback');
  check(lib.t('status.counters.reset') === 'status.counters.reset',
        'without the fallback a missing key shows as before');

  if (fail){ console.error('i18n_fallback_js_check: ' + fail + ' failure(s)'); process.exit(1); }
  console.log('i18n_fallback_js_check: all checks passed');
})();
