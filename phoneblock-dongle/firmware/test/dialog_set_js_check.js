// Checks the dialog set builder of the web UI (main/web/index.html, the
// <dlg-lib> block) against the C test vector in test_dialog.c
// (JS_SET_VECTOR): a set the browser builds must be byte for byte what
// dialog_header_parse() is tested with, and the browser's parser must read
// it back. Also checks the <pcm-lib> port of tts-dialog.py's trim() and
// normalize() keeps its constants. Run by `make test` when node is on PATH.
'use strict';
const fs = require('fs');
const path = require('path');
const html = fs.readFileSync(path.join(__dirname, '../main/web/index.html'), 'utf8');
const block = (tag) => {
  const m = html.match(new RegExp('<' + tag + '>([\\s\\S]*?)// </' + tag + '>'));
  if (!m) throw new Error('no <' + tag + '> block in index.html');
  return m[1];
};
const lib = new Function(block('dlg-lib') + block('pcm-lib') +
  'return {dlgBuild, dlgParse, trimPcm, normalizePcm, TRIM_DBFS, TRIM_KEEP_MS, ' +
  'TARGET_DBFS, PEAK_DBFS, ACTIVE_DBFS};')();

let fail = 0;
const check = (ok, what) => { if (!ok){ fail++; console.error('FAIL ' + what); } };

// The vector: clip counts 1,2,1,3,1, clip k of phase p has 3 + 2*i bytes
// (i counted over all clips) filled with p*16 + k.
const counts = [1, 2, 1, 3, 1];
let i = 0;
const ph = counts.map((c, p) => Array.from({length: c}, (_, k) =>
  new Uint8Array(3 + 2 * i++).fill(p * 16 + k)));
const set = lib.dlgBuild(ph);
const hex = Buffer.from(set).toString('hex');
const c = fs.readFileSync(path.join(__dirname, 'test_dialog.c'), 'utf8');
const m = c.match(/JS_SET_VECTOR\[\] =\s*"([0-9a-f"\s]*)";/);
check(m && m[1].replace(/[\s"]/g, '') === hex, 'JS_SET_VECTOR in test_dialog.c equals the builder output\n  ' + hex);
const back = lib.dlgParse(set);
check(back && back.every((cl, p) => cl.length === counts[p] &&
  cl.every((x, k) => Buffer.compare(Buffer.from(x), Buffer.from(ph[p][k])) === 0)), 'parse(build(x)) == x');
check(lib.dlgParse(set.subarray(0, set.length - 1)) === null, 'truncated set refused');

// The constants of scripts/tts-dialog.py.
const py = fs.readFileSync(path.join(__dirname, '../scripts/tts-dialog.py'), 'utf8');
for (const k of ['TRIM_DBFS', 'TRIM_KEEP_MS', 'TARGET_DBFS', 'PEAK_DBFS', 'ACTIVE_DBFS']){
  const v = py.match(new RegExp('^' + k + ' = (-?[0-9]+)', 'm'));
  check(v && Number(v[1]) === lib[k], k + ' equals tts-dialog.py');
}
// Silence: no trim result; a tone: trimmed and levelled.
check(lib.trimPcm(new Int16Array(800)) === null, 'silence → null');
const tone = new Int16Array(8000);
for (let n = 2000; n < 6000; n++) tone[n] = Math.round(1000 * Math.sin(n / 3));
const tr = lib.trimPcm(tone);
check(tr && tr.length < 4000 + 2 * 400 + 160 && tr.length >= 4000, 'tone trimmed to ±50 ms');
const nm = lib.normalizePcm(tr);
const peak = Math.max(...Array.from(nm, Math.abs));
check(peak > 1000 && peak <= 32768 * 10 ** (-1 / 20) + 1, 'normalized, peak below -1 dBFS');
console.log('dialog_set_js_check: ' + (fail ? fail + ' failures' : 'all checks passed'));
process.exit(fail ? 1 : 0);
