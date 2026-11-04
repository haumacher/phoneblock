// Checks scripts/strip-js-comments.py on the real web UI (#676): every line
// it drops from main/web/index.html and rec.js must be a comment, never text
// inside a template literal, and the stripped scripts must still compile.
// Run by `make test` when node is on PATH.
'use strict';
const fs = require('fs');
const os = require('os');
const path = require('path');
const vm = require('vm');
const { execFileSync } = require('child_process');

const strip = path.join(__dirname, '../scripts/strip-js-comments.py');
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'pb-strip-'));

let fail = 0;
const check = (ok, what) => { if (!ok){ fail++; console.error('FAIL ' + what); } };

// The <script> blocks of a page as [first line index, lines], a .js file whole.
function blocks(lines, html) {
  if (!html) return [[0, lines]];
  const out = [];
  let start = -1;
  lines.forEach((l, i) => {
    if (start < 0 && /<script\b[^>]*>\s*$/i.test(l)) start = i + 1;
    else if (start >= 0 && /^\s*<\/script>/i.test(l)) {
      out.push([start, lines.slice(start, i)]);
      start = -1;
    }
  });
  return out;
}

function compiles(src) {
  try { new vm.Script(src); return true; } catch (e) { return false; }
}

try {
  for (const name of ['index.html', 'rec.js']) {
    const src = path.join(__dirname, '../main/web', name);
    const dst = path.join(tmp, name);
    execFileSync('python3', [strip, src, dst]);
    const orig = fs.readFileSync(src, 'utf8').split('\n');
    const out = fs.readFileSync(dst, 'utf8').split('\n');
    const html = name.endsWith('.html');

    // The output is the input minus some lines: find which.
    const dropped = new Set();
    let j = 0;
    orig.forEach((l, i) => {
      if (j < out.length && out[j] === l) j++;
      else dropped.add(i);
    });
    check(j === out.length, name + ': output is not a subsequence of the input');
    check(dropped.size > 0, name + ': nothing stripped');

    for (const [first, lines] of blocks(orig, html)) {
      const at = name + ': script at line ' + first;
      check(compiles(lines.join('\n')), at + ' does not compile');
      // A dropped line that is a comment turns into a syntax error when replaced
      // by "@@"; inside a template literal it would just be different text.
      for (let k = 0; k < lines.length; k++) {
        if (!dropped.has(first + k)) continue;
        const probe = lines.slice();
        probe[k] = '@@';
        check(!compiles(probe.join('\n')), name + ':' + (first + k + 1)
              + ': dropped line is not a comment: ' + lines[k].trim());
      }
      const kept = lines.filter((_, k) => !dropped.has(first + k));
      check(compiles(kept.join('\n')), at + ' does not compile when stripped');
    }
    // Nothing outside the script blocks may go.
    const inside = new Set();
    for (const [first, lines] of blocks(orig, html))
      lines.forEach((_, k) => inside.add(first + k));
    for (const i of dropped)
      check(inside.has(i), name + ':' + (i + 1) + ': dropped outside <script>');
  }
} finally {
  fs.rmSync(tmp, { recursive: true, force: true });
}
if (fail) { console.error(fail + ' failure(s)'); process.exit(1); }
console.log('strip_web_comments_js_check: ok');
