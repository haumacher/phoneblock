package de.haumacher.phoneblock.analysis;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
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
		System.err.println("Usage: java " + WebCSVTable.class.getName() + " <headers> <url>");
		System.err.println();
		System.err.println("Arguments:");
		System.err.println("  <headers>  Comma-separated list of expected table headers");
		System.err.println("  <url>      URL of the web page to download");
		System.err.println();
		System.err.println("Output:");
		System.err.println("  Writes matching table data to 'out.csv'");
		System.err.println();
		System.err.println("Example:");
		System.err.println("  java " + WebCSVTable.class.getName() + " \\");
		System.err.println("    \"Country,Population,Area\" \\");
		System.err.println("    \"https://example.com/countries.html\"");
	}

	/**
	 * Expects two arguments. First a comma-separated list of expected table headers, second an URL to download.
	 */
	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			printHelp();
			System.exit(1);
		}

		String baseUri = args[1];

		List<String> expectedHeader = Arrays.stream(args[0].split(",")).map(s -> s.trim()).toList();
		
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
					if (!expectedHeader.equals(header)) {
						continue;
					}
					
					for (Element row : rows.subList(1, rows.size())) {
						List<String> content = row.select("td").stream().map(h -> h.text()).toList();
						
						if (content.stream().filter(c -> !c.isBlank()).findAny().isEmpty()) {
							continue;
						}
						
						System.out.println(content.stream().collect(Collectors.joining("\t")));
						
						csv.writeNext(content.toArray(new String[0]));
					}
				}
			}
		}
	}
}
