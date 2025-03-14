#!/bin/bash

# Prüfen, ob ein Ordner als Parameter übergeben wurde
if [ -z "$1" ]; then
    echo "Bitte einen Ordner als Parameter angeben."
    exit 1
fi

INPUT_DIR="$1"

# Prüfen, ob der angegebene Ordner existiert
if [ ! -d "$INPUT_DIR" ]; then
    echo "Der Ordner '$INPUT_DIR' existiert nicht."
    exit 1
fi

# Funktion, um eindeutigen Dateinamen zu erstellen
generate_unique_filename() {
    local dir="$1"
    local base_name="$2"
    local extension="$3"
    local counter=1
    local new_name="${base_name}.${extension}"

    while [ -e "$dir/$new_name" ]; do
        new_name="${base_name}_$counter.${extension}"
        ((counter++))
    done

    echo "$new_name"
}

# Schleife durch alle Audiodateien im Ordner
for file in "$INPUT_DIR"/*.{mp3,flac,wav,m4a,ogg}; do
    # Prüfen, ob Datei existiert
    [ -e "$file" ] || continue

    # Extrahiere Dateinamen ohne Erweiterung
    base_name=$(basename "$file" | sed 's/\.[^.]*$//')
    output_file=$(generate_unique_filename "$INPUT_DIR" "$base_name" "wav")

    echo "Konvertiere: $file -> $INPUT_DIR/$output_file"

    # Konvertierung mit ffmpeg
    ffmpeg -i "$file" -c:a pcm_alaw -ar 8000 -ac 1 "$INPUT_DIR/$output_file"
done

echo "Konvertierung abgeschlossen."
