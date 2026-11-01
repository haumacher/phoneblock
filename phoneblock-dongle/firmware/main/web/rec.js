// Call recordings (#663): the part of the dongle's web UI that only the
// T-Dongle-S3 and the Linux service have. Served at /rec.js (embedded gzipped
// on the S3, a web asset on Linux) and loaded by index.html once /api/status
// reports recording.available — so the 4 MB boards' page carries none of it.
// index.html calls PBRec.status(), PBRec.cell() and PBRec.click(); this file
// uses its t(), esc(), $(), postConfig() and refreshAll().
//
// The dongle stores a recording as raw 8 kHz G.711 A-law
// (GET api/recording?seq=N). Browsers do not reliably play A-law, so it is
// decoded here to 16-bit PCM and wrapped in a PCM WAV for an <audio> element;
// the download is that WAV too, which any player opens.
'use strict';

// <rec-lib> (pure; test/rec_js_check.js checks it against audio.c)
// G.711 A-law byte → 16-bit PCM, the ITU-T reference as audio.c
// alaw_to_pcm() implements it.
function recAlawToPcm(a){
  a ^= 0x55;
  const mant = (a & 0x0f) << 4;
  const seg = (a & 0x70) >> 4;
  const mag = seg === 0 ? mant + 8 : (mant + 0x108) << (seg - 1);
  return (a & 0x80) ? mag : -mag;
}

function recDecode(alaw){
  const pcm = new Int16Array(alaw.length);
  for (let i = 0; i < alaw.length; i++) pcm[i] = recAlawToPcm(alaw[i]);
  return pcm;
}

// 16-bit PCM, mono, 8 kHz → a RIFF/WAVE file (format 1 = PCM, 44-byte header).
function recWav(pcm){
  const out = new Uint8Array(44 + pcm.length * 2);
  const v = new DataView(out.buffer);
  const tag = (o, s) => { for (let i = 0; i < 4; i++) out[o + i] = s.charCodeAt(i); };
  tag(0, 'RIFF'); v.setUint32(4, 36 + pcm.length * 2, true); tag(8, 'WAVE');
  tag(12, 'fmt '); v.setUint32(16, 16, true);
  v.setUint16(20, 1, true);          // PCM
  v.setUint16(22, 1, true);          // mono
  v.setUint32(24, 8000, true);       // sample rate
  v.setUint32(28, 16000, true);      // byte rate
  v.setUint16(32, 2, true);          // block align
  v.setUint16(34, 16, true);         // bits per sample
  tag(36, 'data'); v.setUint32(40, pcm.length * 2, true);
  for (let i = 0; i < pcm.length; i++) v.setInt16(44 + 2 * i, pcm[i], true);
  return out;
}

// m:ss
function recLength(s){
  s = Math.max(0, Math.round(s));
  return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0');
}
// </rec-lib>

window.PBRec = (() => {
  let playing = 0;        // seq whose recording plays, 0: none
  let audio = null;
  let url = '';
  let prefilled = false;

  // The call table is rebuilt on every poll, so the buttons only carry the
  // seq; which of play / stop shows follows `playing`, here and in cell().
  const sync = () => {
    document.querySelectorAll('button[data-recplay]').forEach(b => {
      b.style.display = Number(b.dataset.recplay) === playing ? 'none' : '';
    });
    document.querySelectorAll('button[data-recstop]').forEach(b => {
      b.style.display = Number(b.dataset.recstop) === playing ? '' : 'none';
    });
  };

  const stop = () => {
    if (audio) { audio.pause(); audio = null; }
    if (url) { URL.revokeObjectURL(url); url = ''; }
    playing = 0;
    sync();
  };

  const wavOf = async (seq) => {
    const r = await fetch('api/recording?seq=' + seq);
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return recWav(recDecode(new Uint8Array(await r.arrayBuffer())));
  };

  const play = async (seq, btn) => {
    stop();
    playing = seq;
    sync();
    try {
      const wav = await wavOf(seq);
      if (playing !== seq) return;          // stopped or replaced meanwhile
      url = URL.createObjectURL(new Blob([wav], {type: 'audio/wav'}));
      audio = new Audio(url);
      audio.onended = stop;
      await audio.play();
    } catch (_){
      stop();
      if (btn) btn.title = t('status.rec.failed');
      alert(t('status.rec.failed'));
    }
  };

  const download = async (seq) => {
    try {
      const wav = await wavOf(seq);
      const a = document.createElement('a');
      a.href = URL.createObjectURL(new Blob([wav], {type: 'audio/wav'}));
      a.download = 'call-' + seq + '.wav';
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(a.href), 10000);
    } catch (_){
      alert(t('status.rec.failed'));
    }
  };

  const remove = async (seq) => {
    if (!confirm(t('status.rec.delete.confirm'))) return;
    if (playing === seq) stop();
    try { await fetch('api/recording/delete?seq=' + seq, {method: 'POST'}); }
    catch (_){ /* the next poll shows what is left */ }
    refreshAll();
  };

  // The recording's controls for a row of the call list ('' without one).
  const cell = (c) => {
    if (!c.rec_s || !c.seq) return '';
    const seq = Number(c.seq);
    const btn = (attr, sym, label, hidden) => ' <button class="rowbtn" ' + attr + '="'
      + seq + '" title="' + esc(label) + '" aria-label="' + esc(label) + '"'
      + (hidden ? ' style="display:none"' : '') + '>' + sym + '</button>';
    return '<span class="rec">'
      + btn('data-recplay', '&#9654;', t('status.rec.play'), playing === seq)
      + btn('data-recstop', '&#9632;', t('status.rec.stop'), playing !== seq)
      + ' <span class="muted">' + esc(recLength(c.rec_s)) + '</span>'
      + btn('data-recdl', '&#8681;', t('status.rec.download'), false)
      + btn('data-recdel', '&#10005;', t('status.rec.delete'), false)
      + '</span>';
  };

  // The call table's delegated click handler; true if it was ours.
  const click = (e) => {
    const b = e.target.closest('button[data-recplay],button[data-recstop],'
                               + 'button[data-recdl],button[data-recdel]');
    if (!b) return false;
    if (b.dataset.recplay) play(Number(b.dataset.recplay), b);
    else if (b.dataset.recstop) stop();
    else if (b.dataset.recdl) download(Number(b.dataset.recdl));
    else remove(Number(b.dataset.recdel));
    return true;
  };

  // The switch in the dialog settings, and what the recordings take.
  const status = (r) => {
    const box = $('recSection');
    if (!box) return;
    if (!box.firstChild){
      box.innerHTML = ''
        + '<p class="opt"><label class="check">'
        + '  <input id="recEnabled" type="checkbox">'
        + '  <span>' + esc(t('status.rec.enable')) + '</span></label></p>'
        + '<p class="help">' + esc(t('status.rec.help', {min: Math.round(r.max_s / 60)})) + '</p>'
        + '<p id="recUsage" class="muted status-line"></p>';
      const sw = $('recEnabled');
      sw.onchange = async () => {
        // The notice, once, when it is switched on.
        if (sw.checked && !confirm(t('status.rec.legal'))){
          sw.checked = false;
          return;
        }
        sw.disabled = true;
        try { await postConfig({rec_enabled: sw.checked ? '1' : '0'}); } catch (_){}
        sw.disabled = false;
        refreshAll();
      };
    }
    box.style.display = '';
    const sw = $('recEnabled');
    if (!sw.disabled && (!prefilled || document.activeElement !== sw)) {
      sw.checked = r.enabled === true;
      prefilled = true;
    }
    $('recUsage').textContent = t('status.rec.usage', {
      count: r.count,
      used: (r.used_bytes / 1048576).toFixed(1),
      cap: Math.round(r.cap_bytes / 1048576),
    });
  };

  return {status, cell, click};
})();
