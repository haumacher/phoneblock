// Checks the web UI's status poll (main/web/index.html, the <poll-lib>
// block) pauses in a hidden tab and catches up when it becomes visible
// again (#573), against stubbed document and timers. Run by `make test`
// when node is on PATH.
'use strict';
const fs = require('fs');
const path = require('path');
const html = fs.readFileSync(path.join(__dirname, '../main/web/index.html'), 'utf8');
const m = html.match(/<poll-lib>([\s\S]*?)\/\/ <\/poll-lib>/);
if (!m) throw new Error('no <poll-lib> block in index.html');

const listeners = [];
const document = {
  hidden: false,
  addEventListener: (type, f) => { if (type === 'visibilitychange') listeners.push(f); },
  removeEventListener: (type, f) => {
    const i = listeners.indexOf(f);
    if (i >= 0) listeners.splice(i, 1);
  },
};
const timers = new Map();
let nextId = 1;
const setInterval = (f) => { const id = nextId++; timers.set(id, f); return id; };
const clearInterval = (id) => { timers.delete(id); };
let refreshes = 0;
const refreshAll = () => { refreshes++; };

const lib = new Function('document', 'setInterval', 'clearInterval', 'refreshAll',
  'let pollTimer = null;' + m[1] + 'return {startPolling, stopPolling, whenVisible};')(
  document, setInterval, clearInterval, refreshAll);

let fail = 0;
const check = (ok, what) => { if (!ok){ fail++; console.error('FAIL ' + what); } };
const show = (visible) => { document.hidden = !visible; [...listeners].forEach(f => f()); };
const tick = () => [...timers.values()].forEach(f => f());

lib.startPolling();
check(timers.size === 1, 'polls while visible');
tick();
check(refreshes === 1, 'a tick refreshes');
show(false);
check(timers.size === 0, 'no timer in a hidden tab');
show(true);
check(refreshes === 2 && timers.size === 1, 'refreshes at once and resumes when shown');
lib.startPolling();
check(timers.size === 1, 'a second start adds no second timer');

lib.stopPolling();
show(false); show(true);
check(timers.size === 0 && refreshes === 2, 'stopped stays stopped across visibility');

document.hidden = true;
lib.startPolling();
check(timers.size === 0, 'started hidden: no timer yet');
show(true);
check(timers.size === 1 && refreshes === 3, 'started hidden: begins when shown');
lib.stopPolling();

let ran = 0;
document.hidden = true;
lib.whenVisible(() => ran++);
show(false);
check(ran === 0, 'whenVisible waits while hidden');
show(true); show(true);
check(ran === 1, 'whenVisible runs once when shown');
lib.whenVisible(() => ran++);
check(ran === 2, 'whenVisible runs at once when visible');

if (fail){ console.error('poll_visibility_js_check: ' + fail + ' failure(s)'); process.exit(1); }
console.log('poll_visibility_js_check: all checks passed');
