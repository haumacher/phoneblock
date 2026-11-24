// Checks the web UI's Wi-Fi power field (main/web/index.html, the
// <wifi-power-lib> block) is prefilled once and not overwritten by later
// status polls after the user edits it (#794). Run by `make test` when node
// is on PATH.
'use strict';
const fs = require('fs');
const path = require('path');
const html = fs.readFileSync(path.join(__dirname, '../main/web/index.html'), 'utf8');
const m = html.match(/<wifi-power-lib>([\s\S]*?)\/\/ <\/wifi-power-lib>/);
if (!m) throw new Error('no <wifi-power-lib> block in index.html');

const powerInput = {value: ''};
const document = {activeElement: {}};
const poll = new Function('powerInput', 'document',
  'let wifiPowerPrefilled = false;' +
  'return function(available, txPower){' + m[1] + '};')(
  powerInput, document);

let fail = 0;
const check = (ok, what) => { if (!ok){ fail++; console.error('FAIL ' + what); } };

poll(false, undefined);
check(powerInput.value === '', 'poll without an available value leaves the field empty');
poll(true, 15);
check(powerInput.value === '15', 'first poll fills the available value');
powerInput.value = '18';
document.activeElement = {};
poll(true, 15);
check(powerInput.value === '18', 'a later poll preserves the edited value after blur');

if (fail){ console.error('wifi_power_js_check: ' + fail + ' failure(s)'); process.exit(1); }
console.log('wifi_power_js_check: all checks passed');
