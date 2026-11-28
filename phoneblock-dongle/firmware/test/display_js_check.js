// Display controls are loaded by the status page and reflect its capabilities.
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert');
const js = fs.readFileSync(path.join(__dirname, '../main/web/display.js'), 'utf8');
const html = fs.readFileSync(path.join(__dirname, '../main/web/index.html'), 'utf8');
const loader = html.match(/\/\/ <display-ui>([\s\S]*?)\/\/ <\/display-ui>/);
assert(loader && /window\.PBDisplay\.status\(s\.display\)/.test(loader[1]));
assert(/window\.PBDisplay\.status\(null\)/.test(loader[1]));

// Runs the loader itself against a stub page: a display appearing loads the
// script once (and again after a failed load), one disappearing hides the
// controls.
function checkLoader() {
  const scripts = [];
  const win = {};
  const doc = {
    createElement: () => ({remove() { this.removed = true; }}),
    head: {appendChild: sc => scripts.push(sc)},
  };
  let refreshed = 0;
  const run = s => vm.runInNewContext(
    '(function(s){' + loader[1] + '})', {window: win, document: doc, refreshAll: () => refreshed++})(s);
  run({display: {dim: '60'}});
  run({display: {dim: '60'}});
  assert.strictEqual(scripts.length, 1);
  scripts[0].onerror();
  assert(scripts[0].removed);
  run({display: {dim: '60'}});
  assert.strictEqual(scripts.length, 2);
  scripts[1].onload();
  assert.strictEqual(refreshed, 1);
  let shown = 'unset';
  win.PBDisplay = {status: d => { shown = d; }};
  run({display: {dim: '60'}});
  assert.deepStrictEqual(shown, {dim: '60'});
  run({});
  assert.strictEqual(shown, null);
}
checkLoader();

async function check(buttons) {
  const elements = {
    lcdFlipRow: {insertAdjacentElement: (_, box) => { elements[box.id] = box; }},
    displayDim: {disabled: false, dataset: {}, options: [{value: '60'}]},
    displayWake: {},
    displayMsg: {style: {}},
  };
  const box = {style: {}};
  let response = {ok: false, status: 400, json: async () => ({message: 'not running'})};
  const context = {
    window: {}, document: {createElement: () => box, activeElement: null},
    $: id => elements[id], t: (key, vars) => vars ? vars.msg : key,
    esc: s => s, postConfig: async () => true, refreshAll: () => {},
    fetch: async () => response,
  };
  vm.runInNewContext(js, context);
  assert(context.window.PBDisplay && typeof context.window.PBDisplay.status === 'function');
  context.window.PBDisplay.status({dim: '60', buttons});
  assert(box.innerHTML.includes('status.system.display.dim.hint.buttons') === buttons);
  await elements.displayWake.onclick();
  assert.strictEqual(elements.displayMsg.textContent, 'not running');
  response = null;
  context.fetch = async () => { throw new Error('offline'); };
  await elements.displayWake.onclick();
  assert.strictEqual(elements.displayMsg.textContent, 'offline');
  context.postConfig = async () => false;
  elements.displayDim.value = '300';
  await elements.displayDim.onchange();
  assert.strictEqual(elements.displayMsg.textContent, 'status.dialog.failed');
  context.document.activeElement = elements.displayDim;
  context.window.PBDisplay.status({dim: '60', buttons});
  assert.strictEqual(elements.displayDim.value, '60');
  context.document.activeElement = null;
  context.window.PBDisplay.status(null);
  assert.strictEqual(box.style.display, 'none');
  context.window.PBDisplay.status({dim: '60', buttons});
  assert.strictEqual(box.style.display, '');
}

Promise.all([check(false), check(true)]).then(() => {
  console.log('display_js_check: all checks passed');
}).catch(e => { console.error(e); process.exitCode = 1; });
