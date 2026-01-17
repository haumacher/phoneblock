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

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
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

		// Load trunk prefix mapping
		Map<String, String> trunkPrefixes = new HashMap<>();
		String defaultTrunkPrefix = "0";
		try (InputStream in = Countries.class.getResourceAsStream("trunk-prefixes.json")) {
			if (in != null) {
				JsonReader reader = new JsonReader(new ReaderAdapter(new InputStreamReader(in, StandardCharsets.UTF_8)));
				reader.beginObject();
				while (reader.hasNext()) {
					String key = reader.nextName();
					String value = reader.nextString();
					if ("_default".equals(key)) {
						defaultTrunkPrefix = value;
					} else if (!key.startsWith("_")) {
						trunkPrefixes.put(key, value);
					}
				}
				reader.endObject();
			}
		} catch (IOException e) {
			System.err.println("Warning: Could not load trunk-prefixes.json: " + e.getMessage());
		}

		final String finalDefaultTrunkPrefix = defaultTrunkPrefix;

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

	        	// Set trunk prefix from JSON mapping
	        	String iso2 = country.getISO31661Alpha2();
	        	if (iso2 != null && !iso2.isEmpty()) {
	        		String trunkPrefix = trunkPrefixes.getOrDefault(iso2, finalDefaultTrunkPrefix);
	        		country.setTrunkPrefix(trunkPrefix);
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
			System.out.println(code.getISO31661Alpha2() + " - " + code.getDialPrefixes() + " - " + code.getTrunkPrefix() + " - " + code.getOfficialNameEn());
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
