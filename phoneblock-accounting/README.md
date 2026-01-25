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
java -jar phoneblock-accounting/target/phoneblock-accounting-*-jar-with-dependencies.jar <csv-file>
```

### Example

```bash
java -jar phoneblock-accounting/target/phoneblock-accounting-1.9.0-SNAPSHOT-jar-with-dependencies.jar bank-export-2026.csv
```

## CSV Format

The tool expects CSV files with the following structure:
- Header row with column names
- Columns: Date, Amount, Reference, Description (format may vary by bank)

## Implementation Status

This is a stub implementation. The following features need to be implemented:

- [ ] Parse record fields (date, amount, reference, etc.)
- [ ] Validate record data
- [ ] Store contributions in the PhoneBlock database
- [ ] Handle duplicate detection
- [ ] Support different CSV formats from various banks
- [ ] Match transactions to PhoneBlock users
- [ ] Generate accounting reports

## Testing

Run the unit tests:

```bash
mvn test
```
