// Checks the call recordings' decoding in the web UI (main/web/rec.js, the
// <rec-lib> block, #663): G.711 A-law to 16-bit PCM like audio.c
// alaw_to_pcm(), and the PCM WAV built around it for playback and download.
// Run by `make test` when node is on PATH.
'use strict';
const fs = require('fs');
const path = require('path');
const js = fs.readFileSync(path.join(__dirname, '../main/web/rec.js'), 'utf8');
const m = js.match(/\/\/ <rec-lib>[^\n]*\n([\s\S]*?)\/\/ <\/rec-lib>/);
if (!m) throw new Error('no <rec-lib> block in rec.js');
const lib = new Function(m[1] + 'return {recAlawToPcm, recDecode, recWav, recLength};')();

let fail = 0;
const check = (ok, what) => { if (!ok){ fail++; console.error('FAIL ' + what); } };

// The fixed points of audio.h / test_audio: silence and full scale.
check(lib.recAlawToPcm(0xD5) === 8, '0xD5 -> 8');
check(lib.recAlawToPcm(0x55) === -8, '0x55 -> -8');
check(lib.recAlawToPcm(0xAA) === 32256, '0xAA -> 32256');
check(lib.recAlawToPcm(0x2A) === -32256, '0x2A -> -32256');

// All 256 codes against G.711's own description: a code is the midpoint of
// its quantisation step, 8·(2m+1) in segment 0 and 8·(2m+33)·2^(s-1) above.
for (let c = 0; c < 256; c++) {
  const x = c ^ 0x55;
  const s = (x >> 4) & 7, mant = x & 15;
  const mag = s === 0 ? 8 * (2 * mant + 1) : 8 * (2 * mant + 33) * (1 << (s - 1));
  const want = (x & 0x80) ? mag : -mag;
  check(lib.recAlawToPcm(c) === want, 'code 0x' + c.toString(16) + ': '
        + lib.recAlawToPcm(c) + ' != ' + want);
}
// Never 0 (the recorder's "nothing received" sentinel relies on it).
for (let c = 0; c < 256; c++) check(lib.recAlawToPcm(c) !== 0, 'code ' + c + ' decodes to 0');

// recDecode: byte for byte.
const alaw = Uint8Array.from([0xD5, 0x55, 0xAA, 0x2A, 0x00]);
const pcm = lib.recDecode(alaw);
check(pcm instanceof Int16Array && pcm.length === 5, 'decode length');
check(pcm[2] === 32256 && pcm[3] === -32256 && pcm[4] === lib.recAlawToPcm(0), 'decode values');

// recWav: a 44-byte PCM header, 8 kHz mono 16-bit, little-endian samples.
const wav = lib.recWav(pcm);
const v = new DataView(wav.buffer);
const tag = (o) => String.fromCharCode(wav[o], wav[o + 1], wav[o + 2], wav[o + 3]);
check(wav.length === 44 + 2 * 5, 'wav size');
check(tag(0) === 'RIFF' && v.getUint32(4, true) === wav.length - 8, 'RIFF chunk');
check(tag(8) === 'WAVE' && tag(12) === 'fmt ' && v.getUint32(16, true) === 16, 'fmt chunk');
check(v.getUint16(20, true) === 1 && v.getUint16(22, true) === 1, 'PCM, mono');
check(v.getUint32(24, true) === 8000 && v.getUint32(28, true) === 16000, 'rate');
check(v.getUint16(32, true) === 2 && v.getUint16(34, true) === 16, 'block, bits');
check(tag(36) === 'data' && v.getUint32(40, true) === 10, 'data chunk');
for (let i = 0; i < 5; i++) check(v.getInt16(44 + 2 * i, true) === pcm[i], 'sample ' + i);
// A 5 min recording (the S3's cap per call): the sizes stay consistent.
const big = lib.recWav(new Int16Array(5 * 60 * 8000));
check(big.length === 44 + 2 * 5 * 60 * 8000
      && new DataView(big.buffer).getUint32(40, true) === 2 * 5 * 60 * 8000, 'long wav');

check(lib.recLength(0) === '0:00' && lib.recLength(3) === '0:03'
      && lib.recLength(65) === '1:05' && lib.recLength(1800) === '30:00', 'm:ss');

if (fail) { console.error(fail + ' check(s) failed'); process.exit(1); }
console.log('rec_js_check: all checks passed');
