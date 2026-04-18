#!/usr/bin/env bash
# Convert a voice source file (MP3/WAV/…) to raw G.711 A-law at 8 kHz
# mono — the format the firmware embeds and streams as RTP payload type
# 8 (PCMA). The output overwrites main/audio/announcement.alaw so the
# next `idf.py build` picks it up automatically.
#
# Usage:  ./convert.sh <input-audio-file>

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "usage: $0 <input.mp3|input.wav|…>" >&2
    exit 1
fi

here="$(cd "$(dirname "$0")" && pwd)"
out="$here/announcement.alaw"

ffmpeg -y -loglevel error -i "$1" -ar 8000 -ac 1 -f alaw "$out"

bytes=$(stat -c%s "$out")
frames=$((bytes / 160))
ms=$((frames * 20))
echo "wrote $bytes bytes → $frames frames ≈ ${ms} ms of 20-ms PCMA"
