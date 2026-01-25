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

Run the tool with a CSV file and database connection parameters:

```bash
java -jar phoneblock-accounting/target/phoneblock-accounting-*-jar-with-dependencies.jar <csv-file> [charset] [db-url] [db-user] [db-password]
```

### Arguments

- `<csv-file>` - Path to the CSV file containing bank transactions (required)
- `[charset]` - Character encoding (optional, default: ISO-8859-1)
  - Common values: ISO-8859-1, UTF-8, Windows-1252
- `[db-url]` - Database JDBC URL (optional, default: jdbc:h2:./phoneblock)
- `[db-user]` - Database user (optional, default: phone)
- `[db-password]` - Database password (optional, default: block)

### Prerequisites

The tool requires an existing PhoneBlock database with the CONTRIBUTIONS table already created. It will NOT create the schema automatically.

### Examples

Using defaults (connects to ./phoneblock database):
```bash
java -jar phoneblock-accounting/target/phoneblock-accounting-1.9.0-SNAPSHOT-jar-with-dependencies.jar bank-export-2026.csv
```

Specifying UTF-8 encoding:
```bash
java -jar phoneblock-accounting/target/phoneblock-accounting-1.9.0-SNAPSHOT-jar-with-dependencies.jar bank-export-2026.csv UTF-8
```

Connecting to a specific database:
```bash
java -jar phoneblock-accounting/target/phoneblock-accounting-1.9.0-SNAPSHOT-jar-with-dependencies.jar bank-export-2026.csv ISO-8859-1 jdbc:h2:/path/to/phoneblock
```

With custom credentials:
```bash
java -jar phoneblock-accounting/target/phoneblock-accounting-1.9.0-SNAPSHOT-jar-with-dependencies.jar bank-export-2026.csv ISO-8859-1 jdbc:h2:/path/to/phoneblock myuser mypass
```

## CSV Format

The tool expects CSV files with the following structure:
- Semicolon-delimited (`;`) format
- Header row containing: `Buchung`, `Wertstellungsdatum`, `Auftraggeber/Empfänger`, `Buchungstext`, `Verwendungszweck`, `Betrag`, `Währung`
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

## Implementation Status

Completed features:
- [x] Dynamic header detection in CSV files
- [x] Parse German bank CSV format (semicolon-delimited)
- [x] Filter transactions by "PhoneBlock" keyword in purpose field
- [x] Display all matching transactions with formatted output
- [x] Configurable character encoding (ISO-8859-1 default)
- [x] Configurable database connection (H2)
- [x] Parse and extract transaction details (date, amount, sender)
- [x] Create TX identifier from sender name and date
- [x] Store contributions in the CONTRIBUTIONS table
- [x] Duplicate detection based on TX identifier
- [x] Parse German number format (comma as decimal separator)
- [x] Convert amounts from EUR to cents
- [x] Proper error reporting for missing headers

Features to be implemented:
- [ ] Match transactions to PhoneBlock users by reference ID in Verwendungszweck
- [ ] Generate accounting reports and summaries
- [ ] Support for additional CSV formats from other banks

## Testing

Run the unit tests:

```bash
mvn test
```
