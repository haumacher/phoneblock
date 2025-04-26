#!/bin/bash

# Das Skript kann Audiodateien aus einem `raw`-Unterordner jeweils mit einer 
# Audiodatei `click` konkatenieren, um eine Audiosequenz zu erzeugen, die 
# typisch für eine Gesprächsannahme am Telefon ist. 

SCRIPT_DIR=$(dirname "$0")
click="${SCRIPT_DIR}/../conversation/click.mp3"

if [ ! -f "$click" ]; then
	echo "Das Audio-Präfix '$click' existiert nicht."
	continue
fi

for OUTPUT_DIR in "$@" ; do
	INPUT_DIR="${OUTPUT_DIR}/raw"
	
	# Prüfen, ob der angegebene Ordner existiert
	if [ ! -d "$INPUT_DIR" ]; then
		echo "Der Ordner '$INPUT_DIR' existiert nicht."
		continue
	fi
	
	# Schleife durch alle Audiodateien im Ordner
	for file in "$INPUT_DIR"/*.mp3; do
		# Prüfen, ob Datei existiert
		[ -e "$file" ] || continue
	
		# Extrahiere Dateinamen ohne Erweiterung
		base_name=$(basename "$file" | sed 's/\.[^.]*$//')
		output_name="${base_name}.mp3"
	
		# Konvertierung mit ffmpeg
		output_file="${OUTPUT_DIR}/${output_name}"
		echo "Konvertiere: ${file} -> ${output_file}"
		ffmpeg -y -loglevel quiet -i "concat:${click}|${file}" -acodec copy "${output_file}"
	done
done

echo "Konvertierung abgeschlossen."
