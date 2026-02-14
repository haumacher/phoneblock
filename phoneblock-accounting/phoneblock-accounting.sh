#!/bin/bash
#
# PhoneBlock Accounting Tool Wrapper Script
#
# This script runs the PhoneBlock accounting importer tool.
# It automatically locates the JAR file and passes all arguments through.
#

# Find the script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Find the JAR file in the target directory
JAR_FILE=$(find "$SCRIPT_DIR/target" -name "phoneblock-accounting-*-jar-with-dependencies.jar" -type f 2>/dev/null | head -n 1)

# Check if JAR file exists
if [ -z "$JAR_FILE" ]; then
    echo "Error: JAR file not found in $SCRIPT_DIR/target/" >&2
    echo "Please build the project first using: mvn clean package" >&2
    exit 1
fi

# Check if Java is available
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed or not in PATH" >&2
    exit 1
fi

# Run the accounting tool with all passed arguments
exec java -jar "$JAR_FILE" "$@"
