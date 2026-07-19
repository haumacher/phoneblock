#!/usr/bin/env node
// Regenerate scripts/i18n/l10n/ui/ui_de.arb from the inline `I18N.de` in
// main/web/index.html — the runtime source of truth for the German web UI, so
// German is never duplicated. Run this whenever the German UI text changes,
// then `cd scripts/i18n/l10n && gradle translateArb` and commit.
//
// It MERGES: for a key whose German value is unchanged, the existing @key
// metadata (incl. the plugin's `x-translated` CRC) is preserved so the
// translator skips it; a changed/new key gets a fresh @key with no CRC, so it
// is re-translated. Keys dropped from the UI are dropped here too.
//
//   node scripts/i18n/gen-ui-de-arb.js

const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');            // firmware/
const INDEX = path.join(ROOT, 'main', 'web', 'index.html');
const OUT = path.join(__dirname, 'l10n', 'ui', 'ui_de.arb');

function matchBrace(s, i) {
    let d = 0, q = null, esc = false;
    for (; i < s.length; i++) {
        const c = s[i];
        if (q) { if (esc) esc = false; else if (c === '\\') esc = true; else if (c === q) q = null; continue; }
        if (c === '"' || c === "'" || c === '`') { q = c; continue; }
        if (c === '{') d++; else if (c === '}') { d--; if (d === 0) return i + 1; }
    }
    return -1;
}

const html = fs.readFileSync(INDEX, 'utf8');
const m = html.match(/const\s+I18N\s*=\s*\{/);
const start = m.index + m[0].length - 1;
// eslint-disable-next-line no-eval
const I18N = (0, eval)('(' + html.slice(start, matchBrace(html, start)) + ')');
const de = I18N.de;
if (!de) { console.error('I18N.de not found'); process.exit(1); }

let prev = {};
try { prev = JSON.parse(fs.readFileSync(OUT, 'utf8')); } catch (_) { /* first run */ }

const out = { '@@locale': 'de' };
let kept = 0, fresh = 0;
for (const k of Object.keys(de)) {
    out[k] = de[k];
    if (prev[k] === de[k] && prev['@' + k]) {
        out['@' + k] = prev['@' + k];   // unchanged → keep CRC, skip re-translate
        kept++;
    } else {
        out['@' + k] = { description: `Dongle web UI: ${k}` };
        fresh++;
    }
}
fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(out, null, 2) + '\n');
console.log(`ui_de.arb: ${Object.keys(de).length} keys (${kept} unchanged, ${fresh} to (re)translate)`);
