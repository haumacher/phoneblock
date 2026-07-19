#!/usr/bin/env node
// Print the dongle web UI's inline German string table (I18N.de from
// main/web/index.html) as JSON on stdout.
//
// index.html is the single source of truth for the German base locale; this
// makes it machine-readable so i18n-assets.sh can (a) DeepL-translate it into
// new UI locales that have no reviewed pack yet, and (b) diff it against the
// committed packs. No German UI text is duplicated in the repo.
//
//   node extract-de-ui.js [path/to/index.html] > lang-de.json

const fs = require('fs');
const path = require('path');

const idx = process.argv[2] ||
    path.join(__dirname, '..', '..', 'main', 'web', 'index.html');
const html = fs.readFileSync(idx, 'utf8');

const m = html.match(/const\s+I18N\s*=\s*\{/);
if (!m) { console.error('I18N object not found in ' + idx); process.exit(1); }

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

const start = m.index + m[0].length - 1;
const objSrc = html.slice(start, matchBrace(html, start));
// eslint-disable-next-line no-eval
const I18N = (0, eval)('(' + objSrc + ')');
if (!I18N.de) { console.error('I18N.de missing'); process.exit(1); }
process.stdout.write(JSON.stringify(I18N.de, null, 2) + '\n');
