package de.haumacher.phoneblock.analysis;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;

public class WordTableExtractor {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		XWPFDocument doc = new XWPFDocument(new FileInputStream(new File(args[0])));
		
		
		try (ICSVWriter csv = 
				new CSVWriterBuilder(new FileWriter(new File("out.csv"), StandardCharsets.UTF_8)).withEscapeChar('\\').withLineEnd("\n").withQuoteChar('"').withSeparator(';').build()) {
		
			for (XWPFTable table : doc.getTables()) {
				List<XWPFTableRow> rows = table.getRows();
				
				if (rows.isEmpty()) {
					continue;
				}
				
				for (XWPFTableRow row : rows) {
					List<String> cells = row.getTableCells().stream().map(c -> normalize(c.getText())).toList();
					
					System.out.println(cells.stream().collect(Collectors.joining("\t")));
					
					csv.writeNext(cells.toArray(new String[0]));
				}
			}
		}		
	}

	private static String normalize(String text) {
		if ("not determined".equals(text)) {
			return "";
		}
		return text.replaceAll("\\s+", " ");
	}
}
