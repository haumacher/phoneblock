#!/bin/bash

for INPUT_DIR in "$@" ; do

# Prüfen, ob der angegebene Ordner existiert
if [ ! -d "$INPUT_DIR" ]; then
    echo "Der Ordner '$INPUT_DIR' existiert nicht."
    continue
fi

OUTPUT_DIR="${INPUT_DIR}/PCMA"
mkdir -p "$OUTPUT_DIR"

# Schleife durch alle Audiodateien im Ordner
for file in "$INPUT_DIR"/*.{mp3,flac,wav,m4a,ogg}; do
    # Prüfen, ob Datei existiert
    [ -e "$file" ] || continue

    # Extrahiere Dateinamen ohne Erweiterung
    base_name=$(basename "$file" | sed 's/\.[^.]*$//')
    output_file="${OUTPUT_DIR}/${base_name}.wav"

    echo "Konvertiere: $file -> $output_file"

    # Konvertierung mit ffmpeg
    ffmpeg -y -loglevel quiet -i "$file" -c:a pcm_alaw -ar 8000 -ac 1 "$output_file"
done

done

echo "Konvertierung abgeschlossen."
