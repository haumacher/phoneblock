# Embedded announcement audio

`announcement.alaw` is a raw G.711 A-law stream (8 kHz, mono, no header)
that the firmware embeds via `EMBED_FILES` in `main/CMakeLists.txt` and
plays to spam callers as RTP payload type 8 (PCMA) before hanging up.

The bytes map 1:1 to RTP payload — no conversion at runtime. 160 bytes
= 20 ms at 8 kHz.

## Source

Current source file (committed alongside for reproducibility):

    ElevenLabs_2026-04-18T17_42_21_Leonie_pvc_sp111_s50_sb75_se0_b_m2.mp3

The file name encodes the ElevenLabs voice/model parameters used to
generate the sample, so regenerating a similar take later is possible.

## Regenerate from a new source file

```bash
./convert.sh ElevenLabs_2026-04-18T17_42_21_Leonie_pvc_sp111_s50_sb75_se0_b_m2.mp3
# or any other MP3/WAV/OGG
./convert.sh ~/Downloads/my-new-sample.wav
```

`convert.sh` wraps `ffmpeg -ar 8000 -ac 1 -f alaw …` and overwrites
`announcement.alaw`. After conversion run `idf.py build` — the embedded
file is picked up automatically.

## Keep it short

At 8 KB/s the embedded data size is roughly `(duration_ms / 1000) * 8000`
bytes. Ten seconds of speech cost ≈ 80 KB of flash. The app partition
has plenty of room today, but if the announcement ever grows past ~200 KB
you will want to revisit the partition layout.
