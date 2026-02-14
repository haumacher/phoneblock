# PhoneBlock Accounting

Command-line tool for importing PhoneBlock contribution accounting data from CSV bank exports.

## Purpose

This tool processes CSV files exported from bank accounts and imports contribution payments into the PhoneBlock database for tracking and accounting purposes.

## Building

Build the standalone JAR with all dependencies:

```bash
cd phoneblock-accounting
mvn clean package
```

This creates `phoneblock-accounting-*-jar-with-dependencies.jar` in the `target/` directory.

## Configuration

### Configuration File

The tool supports a configuration file for default settings. By default, it looks for `~/.phoneblock-accounting`.

Create the configuration file with this format:

```properties
# Character encoding for CSV files
charset=ISO-8859-1

# Database connection settings
db.url=jdbc:h2:./phoneblock
db.user=phone
db.password=block
```

A template file is provided at `.phoneblock-accounting.template` in the module directory.

### Command-line Options

```bash
java -jar phoneblock-accounting.jar [options] <csv-file>
```

**Options:**
- `-c, --config <file>` - Configuration file (default: ~/.phoneblock-accounting)
- `-f, --file <csv-file>` - CSV file to import (required, can also be positional)
- `--charset <charset>` - Character encoding (default: ISO-8859-1)
  - Common values: ISO-8859-1, UTF-8, Windows-1252
- `--db-url <url>` - Database JDBC URL (default: jdbc:h2:./phoneblock)
- `--db-user <user>` - Database user (default: phone)
- `--db-password <pass>` - Database password (default: block)
- `-h, --help` - Show help message

Command-line options override configuration file settings.

### Prerequisites

The tool requires an existing PhoneBlock database with the CONTRIBUTIONS table already created. It will NOT create the schema automatically.

### Examples

Using defaults from ~/.phoneblock-accounting:
```bash
java -jar phoneblock-accounting.jar bank-export-2026.csv
```

With named arguments:
```bash
java -jar phoneblock-accounting.jar --file bank-export-2026.csv --charset UTF-8
```

With custom config file:
```bash
java -jar phoneblock-accounting.jar -c /path/to/config bank-export-2026.csv
```

Override database settings:
```bash
java -jar phoneblock-accounting.jar --db-url jdbc:h2:/custom/path bank-export-2026.csv
```

Full example with all options:
```bash
java -jar phoneblock-accounting.jar \
  --file bank-export-2026.csv \
  --charset ISO-8859-1 \
  --db-url jdbc:h2:/var/lib/phoneblock \
  --db-user myuser \
  --db-password mypass
```

## CSV Format

The tool expects CSV files with the following structure:
- Semicolon-delimited (`;`) format
- Header row containing: `Buchung`, `Wertstellungsdatum`, `Auftraggeber/Empfänger`, `Buchungstext`, `Verwendungszweck`, `Betrag`, `Währung`
- Columns can appear in any order - the tool automatically detects column positions from the header
- The tool automatically skips metadata rows before the header
- Only transactions containing "PhoneBlock" (case-insensitive) in the `Verwendungszweck` column are processed

## Transaction Identification

Each contribution is identified by the `TX` column in the database, which consists of:
- Sender name (from `Auftraggeber/Empfänger`)
- Booking date (from `Buchung` in format DD.MM.YYYY)
- Format: `"Sender Name DD.MM.YYYY"` (e.g., "Max Mustermann 10.03.2025")

The tool automatically:
- Checks if a contribution with the same TX identifier already exists
- Skips duplicate transactions
- Inserts only new contributions into the database

## Testing

Run the unit tests:

```bash
mvn test
```
