package de.haumacher.phoneblock.analysis;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;

/**
 * Tool to download a web page and extract tabular data from the page and store it in CSV format.
 */
public class WebCSVTable {

	private static void printHelp() {
		System.err.println("WebCSVTable - Extract table data from web pages to CSV format");
		System.err.println();
		System.err.println("Usage: java " + WebCSVTable.class.getName() + " <headers> <url> [-rCOLUMN=s/PATTERN/REPLACEMENT/[FLAGS]]...");
		System.err.println();
		System.err.println("Arguments:");
		System.err.println("  <headers>  Comma-separated list of expected table headers");
		System.err.println("             (can be a subset and in any order)");
		System.err.println("  <url>      URL of the web page to download");
		System.err.println();
		System.err.println("Options:");
		System.err.println("  -rCOLUMN=s/PATTERN/REPLACEMENT/[FLAGS]");
		System.err.println("             Apply regex replacement to specified column");
		System.err.println("             The delimiter after 's' can be any character (/, |, #, etc.)");
		System.err.println("             Multiple -r options can be specified");
		System.err.println("             Flags: g (global replace), i (case-insensitive)");
		System.err.println();
		System.err.println("Output:");
		System.err.println("  Writes matching table data to 'out.csv'");
		System.err.println();
		System.err.println("Examples:");
		System.err.println("  Basic usage:");
		System.err.println("    java " + WebCSVTable.class.getName() + " \\");
		System.err.println("      \"Country,Population,Area\" \\");
		System.err.println("      \"https://example.com/countries.html\"");
		System.err.println();
		System.err.println("  With replacements:");
		System.err.println("    java " + WebCSVTable.class.getName() + " \\");
		System.err.println("      \"Country,Population\" \\");
		System.err.println("      \"https://example.com/countries.html\" \\");
		System.err.println("      -rCountry=s/Republic/Rep./g \\");
		System.err.println("      -rCountry=s|United States|USA| \\");
		System.err.println("      -rPopulation=s/,//g");
	}

	/**
	 * Expects two arguments. First a comma-separated list of expected table headers, second an URL to download.
	 * Optional -r arguments specify column replacements.
	 */
	public static void main(String[] args) throws IOException {
		if (args.length < 2) {
			printHelp();
			System.exit(1);
		}

		// Parse arguments: first two are positional, rest are options
		String headersArg = args[0];
		String baseUri = args[1];

		// Parse -r options for column replacements
		// Map of column name -> list of replacements to apply in order
		Map<String, List<Replacement>> columnReplacements = new HashMap<>();

		for (int i = 2; i < args.length; i++) {
			String arg = args[i];
			if (arg.startsWith("-r")) {
				// Format: -rColumnName=s/pattern/replacement/flags
				String spec = arg.substring(2); // Remove "-r"
				int equalsPos = spec.indexOf('=');
				if (equalsPos == -1) {
					System.err.println("Invalid replacement specification (missing '='): " + arg);
					System.err.println("Expected format: -rCOLUMN=s/PATTERN/REPLACEMENT/[FLAGS]");
					System.exit(1);
				}

				String columnName = spec.substring(0, equalsPos);
				String replacementSpec = spec.substring(equalsPos + 1);

				try {
					Replacement replacement = parseReplacement(replacementSpec);
					columnReplacements.computeIfAbsent(columnName, k -> new ArrayList<>()).add(replacement);
				} catch (IllegalArgumentException e) {
					System.err.println("Error parsing replacement for column '" + columnName + "': " + e.getMessage());
					System.exit(1);
				}
			} else {
				System.err.println("Unknown argument: " + arg);
				printHelp();
				System.exit(1);
			}
		}

		List<String> expectedHeader = Arrays.stream(headersArg.split(",")).map(s -> s.trim()).toList();
		
		try (ICSVWriter csv = 
			new CSVWriterBuilder(new FileWriter(new File("out.csv"), StandardCharsets.UTF_8)).withEscapeChar('\\').withLineEnd("\n").withQuoteChar('"').withSeparator(';').build()) {
			
			URL url = new URL(baseUri);
			URLConnection connection = url.openConnection();
			String encoding = connection.getContentEncoding();
			try (InputStream in = url.openStream()) {
				Document document = Jsoup.parse(in, encoding, baseUri);
				
				Elements tables = document.select("table");
				for (Element table : tables) {
					Elements rows = table.select("tr");
					if (rows.isEmpty()) {
						continue;
					}

					List<String> header  = rows.get(0).select("th").stream().map(h -> h.text()).toList();

					// Check if all expected headers are present in the table
					if (!header.containsAll(expectedHeader)) {
						continue;
					}

					// Map expected headers to their column indices in the table
					int[] columnIndices = new int[expectedHeader.size()];
					for (int i = 0; i < expectedHeader.size(); i++) {
						columnIndices[i] = header.indexOf(expectedHeader.get(i));
					}

					// Write CSV header with expected column order
					csv.writeNext(expectedHeader.toArray(new String[0]));

					for (Element row : rows.subList(1, rows.size())) {
						List<String> allColumns = row.select("td").stream().map(h -> h.text()).toList();

						if (allColumns.stream().filter(c -> !c.isBlank()).findAny().isEmpty()) {
							continue;
						}

						// Extract only the requested columns in the specified order
						List<String> selectedColumns = new ArrayList<>();
						for (int i = 0; i < columnIndices.length; i++) {
							int index = columnIndices[i];
							String value;
							if (index < allColumns.size()) {
								value = allColumns.get(index);
							} else {
								value = "";
							}

							// Apply replacements for this column
							String columnName = expectedHeader.get(i);
							List<Replacement> replacements = columnReplacements.get(columnName);
							if (replacements != null) {
								for (Replacement replacement : replacements) {
									value = replacement.apply(value);
								}
							}

							selectedColumns.add(value);
						}

						System.out.println(selectedColumns.stream().collect(Collectors.joining("\t")));

						csv.writeNext(selectedColumns.toArray(new String[0]));
					}
				}
			}
		}
	}

	/**
	 * Parses a replacement specification in sed-style format: s/pattern/replacement/[flags]
	 * Supports flexible delimiters (the character after 's' is used as delimiter).
	 *
	 * @param spec The replacement specification (e.g., "s/old/new/g" or "s|old|new|")
	 * @return A Replacement object
	 * @throws IllegalArgumentException if the specification is invalid
	 */
	static Replacement parseReplacement(String spec) {
		if (spec.length() < 4 || spec.charAt(0) != 's') {
			throw new IllegalArgumentException("Replacement must start with 's' followed by delimiter: " + spec);
		}
	
		char delimiter = spec.charAt(1);
		int pos = 2;
	
		// Extract pattern
		StringBuilder pattern = new StringBuilder();
		while (pos < spec.length() && spec.charAt(pos) != delimiter) {
			if (spec.charAt(pos) == '\\' && pos + 1 < spec.length()) {
				// Handle escaped characters
				pattern.append(spec.charAt(pos));
				pattern.append(spec.charAt(pos + 1));
				pos += 2;
			} else {
				pattern.append(spec.charAt(pos));
				pos++;
			}
		}
	
		if (pos >= spec.length()) {
			throw new IllegalArgumentException("Missing replacement delimiter in: " + spec);
		}
		pos++; // Skip delimiter
	
		// Extract replacement
		StringBuilder replacement = new StringBuilder();
		while (pos < spec.length() && spec.charAt(pos) != delimiter) {
			if (spec.charAt(pos) == '\\' && pos + 1 < spec.length()) {
				// Handle escaped characters
				replacement.append(spec.charAt(pos));
				replacement.append(spec.charAt(pos + 1));
				pos += 2;
			} else {
				replacement.append(spec.charAt(pos));
				pos++;
			}
		}
	
		if (pos >= spec.length()) {
			throw new IllegalArgumentException("Missing final delimiter in: " + spec);
		}
		pos++; // Skip delimiter
	
		// Extract flags
		String flags = pos < spec.length() ? spec.substring(pos) : "";
		boolean global = flags.contains("g");
		boolean caseInsensitive = flags.contains("i");
	
		int patternFlags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
		Pattern regexPattern = Pattern.compile(pattern.toString(), patternFlags);
	
		return new Replacement(regexPattern, replacement.toString(), global);
	}

	/**
	 * Represents a single regex replacement rule for a column.
	 */
	static class Replacement {
		final Pattern pattern;
		final String replacement;
		final boolean global;
	
		Replacement(Pattern pattern, String replacement, boolean global) {
			this.pattern = pattern;
			this.replacement = replacement;
			this.global = global;
		}
	
		String apply(String input) {
			if (global) {
				return pattern.matcher(input).replaceAll(replacement);
			} else {
				return pattern.matcher(input).replaceFirst(replacement);
			}
		}
	}
}
