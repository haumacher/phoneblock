package de.haumacher.phoneblock.location;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.exceptions.CsvValidationException;

import de.haumacher.phoneblock.location.model.Country;

/**
 * List of countries loaded from the database provided at https://github.com/datasets/country-codes.
 */
public class Countries {
	
	public static final Map<String, Country> BY_ISO_31661_ALPHA_2;
	public static final Map<String, List<Country>> BY_DIAL_PREFIX;
	public static final Map<String, List<Country>> BY_LANG;
	
	static {
		Map<String, Country> byISO31661Alpha2 = new LinkedHashMap<>();
		Map<String, List<Country>> byDialPrefix = new LinkedHashMap<>();
		Map<String, List<Country>> byLang = new LinkedHashMap<>();

		// Load trunk and international prefixes from CSV
		Map<String, List<String>> trunkPrefixMap = new HashMap<>();
		Map<String, List<String>> internationalPrefixMap = new HashMap<>();

		try (InputStream csvIn = Countries.class.getResourceAsStream("trunk-prefixes.csv")) {
			if (csvIn != null) {
				CSVReaderHeaderAware csvReader = new CSVReaderHeaderAware(new InputStreamReader(csvIn, StandardCharsets.UTF_8));
				Map<String, String> csvLine;
				while ((csvLine = csvReader.readMap()) != null) {
					String isoCode = csvLine.get("ISO_Code");
					String internationalPrefix = csvLine.get("International_Prefix");
					String trunkPrefix = csvLine.get("Trunk_Prefix");

					if (isoCode != null && !isoCode.isEmpty()) {
						// Parse international prefix (may contain multiple values separated by /)
						if (internationalPrefix != null && !internationalPrefix.isEmpty()) {
							List<String> intPrefixes = Arrays.stream(internationalPrefix.split("/"))
									.map(String::strip)
									.filter(p -> !p.isEmpty())
									.toList();
							internationalPrefixMap.put(isoCode, intPrefixes);
						}

						// Parse trunk prefix (may contain multiple values separated by /)
						if (trunkPrefix != null && !trunkPrefix.isEmpty()) {
							List<String> tPrefixes = Arrays.stream(trunkPrefix.split("/"))
									.map(String::strip)
									.filter(p -> !p.isEmpty())
									.toList();
							trunkPrefixMap.put(isoCode, tPrefixes);
						}
					}
				}
			}
		} catch (IOException | CsvValidationException e) {
			System.err.println("Warning: Could not load trunk-prefixes.csv: " + e.getMessage());
			e.printStackTrace();
		}

        try (InputStream in = Countries.class.getResourceAsStream("country-codes.csv")) {
			CSVReaderHeaderAware reader = new CSVReaderHeaderAware(new InputStreamReader(in, StandardCharsets.UTF_8));
	        Map<String, String> line;
			while ((line = reader.readMap()) != null) {
				Country country = Country.create();
	        	for (Entry<String, String> attr : line.entrySet()) {
	        		switch (attr.getKey()) {
	        		case Country.INDEPENDENT__PROP: {
	        			country.setIndependent("Yes".equals(attr.getValue()));
	        			break;
	        		}
	        		case Country.LANGUAGES__PROP: {
	        			country.set(attr.getKey(), Arrays.stream(attr.getValue().split(",")).map(String::strip).filter(l -> !l.isEmpty()).toList());
	        			break;
	        		}
	        		case Country.DIAL_PREFIXES__PROP: {
	        			country.set(attr.getKey(), Arrays.stream(attr.getValue().split(","))
	        					.map(String::strip)
	        					.map(p -> p.replace("-", ""))
	        					.map(Countries::number)
	        					.filter(l -> !l.isEmpty())
	        					.map(p -> "+" + p)
	        					.toList());
	        			break;
	        		}
	        		default: {
	        			country.set(attr.getKey(), attr.getValue());
	        		}
	        		}
	        	}

	        	// Set trunk and international prefixes from CSV mapping
	        	String iso2 = country.getISO31661Alpha2();
	        	if (iso2 != null && !iso2.isEmpty()) {
	        		List<String> trunkPrefixes = trunkPrefixMap.get(iso2);
	        		if (trunkPrefixes != null && !trunkPrefixes.isEmpty()) {
	        			country.setTrunkPrefixes(trunkPrefixes);
	        		}

	        		List<String> internationalPrefixes = internationalPrefixMap.get(iso2);
	        		if (internationalPrefixes != null && !internationalPrefixes.isEmpty()) {
	        			country.setInternationalPrefixes(internationalPrefixes);
	        		}
	        	}

	        	byISO31661Alpha2.put(country.getISO31661Alpha2(), country);

	        	for (String lang : country.getLanguages()) {
	        		byLang.computeIfAbsent(lang, x -> new ArrayList<>()).add(country);
	        	}
	        	for (String dialPrefix : country.getDialPrefixes()) {
	        		byDialPrefix.computeIfAbsent(dialPrefix, x -> new ArrayList<>()).add(country);
	        	}
	        }
		} catch (IOException | CsvValidationException e) {
			e.printStackTrace();
		}

        BY_ISO_31661_ALPHA_2 = Collections.unmodifiableMap(byISO31661Alpha2);
        BY_DIAL_PREFIX = Collections.unmodifiableMap(byDialPrefix);
        BY_LANG = Collections.unmodifiableMap(byLang);
	}

	private static String number(String str) {
		StringBuilder result = new StringBuilder();
		for (int n = 0, len = str.length(); n < len; n++) {
			char ch = str.charAt(n);
			if (Character.isDigit(ch)) {
				result.append(ch);
			}
		}
		return result.toString();
	}
	
	public static void main(String[] args) {
		List<Country> codes = new ArrayList<>(BY_ISO_31661_ALPHA_2.values());
		for (Country code : codes) {
			System.out.println(code.getISO31661Alpha2() + " - " + code.getDialPrefixes() + " - Trunk:" + code.getTrunkPrefixes() + " - Intl:" + code.getInternationalPrefixes() + " - " + code.getOfficialNameEn());
		}

		List<String> langs= new ArrayList<>(BY_LANG.keySet());
		Collections.sort(langs);
		for (String lang : langs) {
			List<Country> countries = BY_LANG.get(lang);
			if (countries.size() == 1) {
				continue;
			}

			countries = countries.stream().sorted(Comparator.comparingInt(c -> c.getLanguages().size())).toList();
			
			System.out.println(lang + " (" + Locale.forLanguageTag(lang).getDisplayName() + ")" + " - " + countries.stream().map(c -> c.getISO31661Alpha2() + " (" + c.getOfficialNameEn() + " " + c.getDialPrefixes() + ")").collect(Collectors.joining(", ")));
		}
	}

	public static Country get(String iso31661alpha2) {
		return BY_ISO_31661_ALPHA_2.get(iso31661alpha2);
	}

	public static String selectDialPrefix(String dialPrefix) {
		return fromDialPrefix(dialPrefix) == null ? null : dialPrefix;
	}

	public static Collection<Country> all() {
		return BY_ISO_31661_ALPHA_2.values();
	}

	public static List<Country> fromDialPrefix(String dialPrefix) {
		return BY_DIAL_PREFIX.get(dialPrefix);
	}
}
