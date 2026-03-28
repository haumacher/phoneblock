#!/bin/bash
# Run the fake-mail-check CLI tool.
# Usage: ./mailcheck.sh [--db <path>] <command> [args...]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$(ls "$SCRIPT_DIR"/target/fake-mail-check-*-jar-with-dependencies.jar 2>/dev/null | head -1)"

if [ -z "$JAR" ]; then
    echo "JAR not found. Building..." >&2
    mvn -f "$SCRIPT_DIR/pom.xml" -q package -DskipTests || exit 1
    JAR="$(ls "$SCRIPT_DIR"/target/fake-mail-check-*-jar-with-dependencies.jar | head -1)"
fi

exec java -jar "$JAR" "$@"
