#!/bin/bash

for INPUT_DIR in "$@" ; do

# Prüfen, ob der angegebene Ordner existiert
if [ ! -d "$INPUT_DIR" ]; then
    echo "Der Ordner '$INPUT_DIR' existiert nicht."
    continue
fi

OUTPUT_DIR_8="${INPUT_DIR}/PCMA"
mkdir -p "$OUTPUT_DIR_8"

OUTPUT_DIR_16="${INPUT_DIR}/PCMA-WB"
mkdir -p "$OUTPUT_DIR_16"

# Schleife durch alle Audiodateien im Ordner
for file in "$INPUT_DIR"/*.{mp3,flac,wav,m4a,ogg}; do
    # Prüfen, ob Datei existiert
    [ -e "$file" ] || continue

    # Extrahiere Dateinamen ohne Erweiterung
    base_name=$(basename "$file" | sed 's/\.[^.]*$//')
    output_name="${base_name}.wav"

    # Konvertierung mit ffmpeg
    output_file_8="${OUTPUT_DIR_8}/${base_name}.wav"
    echo "Konvertiere: $file -> ${output_file_8}"
    ffmpeg -y -loglevel quiet -i "$file" -c:a pcm_alaw -ar 8000 -ac 1 "${output_file_8}"
    
    output_file_16="${OUTPUT_DIR_16}/${base_name}.wav"
    echo "Konvertiere: $file -> ${output_file_16}"
    ffmpeg -y -loglevel quiet -i "$file" -c:a pcm_alaw -ar 16000 -ac 1 "${output_file_16}"
done

done

echo "Konvertierung abgeschlossen."
