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

## Usage

Run the tool with a CSV file:

```bash
java -jar phoneblock-accounting/target/phoneblock-accounting-*-jar-with-dependencies.jar <csv-file> [charset]
```

### Arguments

- `<csv-file>` - Path to the CSV file containing bank transactions (required)
- `[charset]` - Character encoding (optional, default: ISO-8859-1)
  - Common values: ISO-8859-1, UTF-8, Windows-1252

### Examples

Using default ISO-8859-1 encoding (German banks):
```bash
java -jar phoneblock-accounting/target/phoneblock-accounting-1.9.0-SNAPSHOT-jar-with-dependencies.jar bank-export-2026.csv
```

Specifying UTF-8 encoding:
```bash
java -jar phoneblock-accounting/target/phoneblock-accounting-1.9.0-SNAPSHOT-jar-with-dependencies.jar bank-export-2026.csv UTF-8
```

## CSV Format

The tool expects CSV files with the following structure:
- Semicolon-delimited (`;`) format
- Header row containing: `Buchung`, `Wertstellungsdatum`, `Auftraggeber/Empfänger`, `Buchungstext`, `Verwendungszweck`, `Betrag`, `Währung`
- The tool automatically skips metadata rows before the header
- Only transactions containing "PhoneBlock" (case-insensitive) in the `Verwendungszweck` column are processed

## Implementation Status

Completed features:
- [x] Dynamic header detection in CSV files
- [x] Parse German bank CSV format (semicolon-delimited)
- [x] Filter transactions by "PhoneBlock" keyword in purpose field
- [x] Display all matching transactions with formatted output
- [x] Configurable character encoding (ISO-8859-1 default)
- [x] Proper error reporting for missing headers

Features to be implemented:
- [ ] Parse and extract transaction details (date, amount, reference ID)
- [ ] Validate record data (amounts, dates, reference format)
- [ ] Store contributions in the PhoneBlock database
- [ ] Handle duplicate detection
- [ ] Match transactions to PhoneBlock users by reference ID
- [ ] Generate accounting reports and summaries

## Testing

Run the unit tests:

```bash
mvn test
```
