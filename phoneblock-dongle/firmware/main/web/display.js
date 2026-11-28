// The display's part of the dongle's web UI (#773): how long the backlight
// stays on, and a button that turns it on. Served at /display.js on the
// boards with a display and loaded by index.html once /api/status carries
// display.dim, so the C3's page has none of it. index.html calls
// PBDisplay.status(); this file uses its t(), esc(), $(), postConfig() and
// refreshAll().
'use strict';

window.PBDisplay = (() => {
  // Seconds; '' is the default (60 s), 'always' only where the board offers it.
  const CHOICES = ['30', '60', '120', '300'];

  const build = (box, d) => {
    const opts = CHOICES.map((v) => '<option value="' + v + '">'
      + esc(t('status.system.display.dim.' + v)) + '</option>');
    if (d.dim_always_ok){
      opts.push('<option value="always">' + esc(t('status.system.display.dim.always')) + '</option>');
    }
    box.innerHTML = ''
      + '<p class="opt"><label for="displayDim">' + esc(t('status.system.display.dim')) + '</label> '
      + '<select id="displayDim">' + opts.join('') + '</select></p>'
      + '<p class="hint">' + esc(t('status.system.display.dim.hint')) + '</p>'
      + (d.buttons ? '<p class="hint">' + esc(t('status.system.display.dim.hint.buttons')) + '</p>' : '')
      + '<p class="opt"><button id="displayWake" type="button">'
      + esc(t('status.system.display.wake')) + '</button></p>'
      + '<p id="displayMsg" class="hint"></p>';
    const sel = $('displayDim');
    sel.onchange = async () => {
      sel.disabled = true;
      const msg = $('displayMsg');
      msg.textContent = '';
      let ok = false;
      try { ok = await postConfig({display_dim: sel.value}); } catch (_){}
      if (!ok){
        msg.textContent = t('status.dialog.failed');
        msg.style.color = 'var(--err)';
        sel.dataset.stale = '1';
      }
      sel.disabled = false;
      refreshAll();   // the next status resets the select to the saved value
    };
    const wake = $('displayWake');
    wake.onclick = async () => {
      wake.disabled = true;
      const msg = $('displayMsg');
      msg.textContent = '';
      try {
        const r = await fetch('api/display/wake', {method: 'POST'});
        const data = await r.json().catch(() => ({}));
        if (!r.ok || !data.ok) throw new Error(data.message || ('HTTP ' + r.status));
      } catch (e){
        msg.textContent = t('status.fw.error', {msg: e.message || 'Network error'});
        msg.style.color = 'var(--err)';
      }
      wake.disabled = false;
    };
  };

  const status = (d) => {
    const row = $('lcdFlipRow');
    if (!row) return;
    let box = $('displayExtra');
    if (!d){
      if (box) box.style.display = 'none';
      return;
    }
    if (!box){
      box = document.createElement('div');
      box.id = 'displayExtra';
      row.insertAdjacentElement('afterend', box);
      build(box, d);
    }
    box.style.display = '';
    const sel = $('displayDim');
    if (sel.dataset.stale || (!sel.disabled && document.activeElement !== sel)){
      delete sel.dataset.stale;
      // The default (empty) is 60 s; a value the list lacks shows as that too.
      const v = d.dim || '60';
      sel.value = [...sel.options].some((o) => o.value === v) ? v : '60';
    }
  };

  return {status};
})();
