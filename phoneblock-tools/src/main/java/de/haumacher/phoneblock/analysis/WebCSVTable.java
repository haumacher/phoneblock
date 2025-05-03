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

	/**
	 * Expects two arguments. First a comma-separated list of expected table headers, second an URL to download.
	 */
	public static void main(String[] args) throws IOException {
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
