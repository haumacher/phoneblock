/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.ICSVParser;
import com.opencsv.exceptions.CsvValidationException;

import de.haumacher.phoneblock.analysis.model.NationalDestinationCode;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.location.Countries;
import de.haumacher.phoneblock.location.model.Country;
import de.haumacher.phoneblock.shared.PhoneHash;

/**
 * Utility for analyzing phone numbers.
 */
public class NumberAnalyzer {

	/**
	 * The dial prefix for Germany. For historical reasons, German phone numbers are
	 * stored in national format in the database.
	 */
	public static final String GERMAN_DIAL_PREFIX = "+49";

	private static final Logger LOG = LoggerFactory.getLogger(NumberAnalyzer.class);

	private static Node PREFIX_TREE = buildTree();

	/** 
	 * Creates a database ID for the given phone number, or <code>null</code> if the number is invalid. 
	 */
	public static String toId(String phoneText) {
		return toId(phoneText, GERMAN_DIAL_PREFIX);
	}
	
	public static String toId(String phoneText, String dialPrefix) {
		PhoneNumer number = parsePhoneNumber(phoneText, dialPrefix);
		return number == null ? null : getPhoneId(number);
	}

	public static PhoneNumer parsePhoneNumber(String phoneText) {
		return parsePhoneNumber(phoneText, GERMAN_DIAL_PREFIX);
	}
	
	public static PhoneNumer parsePhoneNumber(String phoneText, String dialPrefix) {
		String phoneNumber = normalizeNumber(phoneText);
		if (phoneNumber.contains("*")) {
			LOG.warn("Ignoring number with wildcard: " + phoneText);
			return null;
		}
		
		return analyze(phoneNumber, dialPrefix);
	}

	/**
	 * A number must only consist of digits optionally prefixed by a '+' sign and
	 * optionally ending in a '*' sign. All other characters must be discarded.
	 */
	private static final Pattern NORMALIZE_PATTERN = Pattern.compile("^[^\\+0-9]|(?<=.)[^0-9](?=.)|[^0-9\\*]$");

	/** 
	 * Removes grouping characters from the given phone number.
	 */
	public static String normalizeNumber(String phoneNumber) {
		return NORMALIZE_PATTERN.matcher(phoneNumber).replaceAll("");
	}

	public static PhoneNumer analyze(String phone) {
		return analyze(phone, GERMAN_DIAL_PREFIX);
	}
	
	/**
	 * Analyzes the given (normalized) phone number.
	 * @param dialPrefix The users local dial prefix.
	 */
	public static PhoneNumer analyze(String phone, String dialPrefix) {
		PhoneNumer result = PhoneNumer.create();
		
		String plus = PhoneHash.toInternationalForm(phone, dialPrefix);
		if (plus == null) {
			// Not a valid number.
			return null;
		}
		result.setPlus(plus);
		String zeroZero = "00" + plus.substring(1);
		result.setZeroZero(zeroZero);
		
		PrefixInfo info = findInfo(plus);
		
		String countryCode = info.getCountryCode();
		if (countryCode == null || plus.charAt(countryCode.length()) == '0') {
			// A city code cannot start with a zero.
			return null;
		}

		// Numbers seen in real live seem to exceed the maximum digit size in the numbering plan. 
		// E.g. +49-9131-9235017072 from Erlangen(9131)/Germany(49) is a number observed by many users, 
		// even if the numbering plan for Germany states that numbers with the prefix 9131 have 
		// a maximum length of 11 digits (9131-9235017-072). Does this mean that the suffix (072) that 
		// exceeds the digits in the numbering plan is a direct dialing suffix for the 
		// connection 9131-9235017?
		// 
//		int maxDigits = info.getMaxDigits();
//		if (maxDigits >= 0) {
//			if (plus.length() > maxDigits + countryCode.length()) {
//				return null;
//			}
//		}
		
		int minDigits = info.getMinDigits();
		if (minDigits >= 0) {
			if (plus.length() < minDigits + countryCode.length()) {
				return null;
			}
		}
		
		result.setCountryCode(countryCode);
		List<Country> countries = info.getCountries();
		result.setCityCode(info.getCityCode());
		result.setCity(info.getCity());
		result.setUsage(info.getUsage());
		
		if (countries.isEmpty()) {
			// There is no shortcut.
			result.setCountry("Unknown");
			result.setShortcut(plus);
			result.setId(zeroZero);
		} else {
			result.setCountry(countries.stream().map(c -> c.getOfficialNameEn()).collect(Collectors.joining(", ")));
			String national = "0" + plus.substring(countryCode.length());
			result.setShortcut("(" + countries.stream().map(c -> c.getISO31661Alpha2()).collect(Collectors.joining(", ")) + ") " + national);
			if (GERMAN_DIAL_PREFIX.equals(countryCode)) {
				result.setId(national);
			} else {
				result.setId(zeroZero);
			}
		}
		
		return result;
	}

	/**
	 * Creates an database ID for the given analyzed {@link PhoneNumer}.
	 */
	public static String getPhoneId(PhoneNumer number) {
		return number.getId();
	}

	private static Node buildTree() {
		Node root = new Node('+');
		root._countries = null;
		for (Country country : Countries.all()) {
			for (String dialPrefix : country.getDialPrefixes()) {
				Node node = root.enter(dialPrefix, 1);
				node._contryCode = dialPrefix;
				if (node._countries.isEmpty()) {
					node._countries = Collections.singletonList(country);
				} else {
					ArrayList<Country> update = new ArrayList<>(node._countries);
					update.add(country);
					node._countries = update.stream().filter(c -> c.isIndependent()).toList();
				}
			}
		}
		
		loadNumberingPlan(root, GERMAN_DIAL_PREFIX, "numbering-plan-49-germany.csv");
		loadNumberingPlan(root, "+43", "numbering-plan-43-austria.csv");
		loadNumberingPlan(root, "+39", "numbering-plan-39-italy.csv");
		
		return root;
	}

	private static void loadNumberingPlan(Node root, String dialPrefix, String resource) {
		// See https://www.itu.int/oth/T0202.aspx?lang=en&parent=T0202
		// See https://www.bundesnetzagentur.de/SharedDocs/Downloads/DE/Sachgebiete/Telekommunikation/Unternehmen_Institutionen/Nummerierung/Rufnummern/ONRufnr/Vorwahlverzeichnis_ONB.zip.html
		try (InputStream in = NumberAnalyzer.class.getResourceAsStream(resource)) {
			try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				Node countryNode = root.find(dialPrefix, 1, root);
				
				ICSVParser parser = new CSVParserBuilder().withSeparator(';').withStrictQuotes(false).build();
		        try (CSVReader csv = new CSVReaderBuilder(r).withCSVParser(parser).build()) {
		        	// Skip header.
		        	csv.readNext();
		        	while (true) {
		        		String[] line = csv.readNext();
		        		if (line == null || line.length < 3) {
		        			break;
		        		}
		        		
		        		String prefix = line[0].replaceAll("[^0-9]", "");
		        		int maxDigits = line[1].isBlank() ? -1 : Integer.parseInt(line[1]);
		        		int minDigits = line[2].isBlank() ? -1 : Integer.parseInt(line[2]);
		        		String usage = line[3];
		        		String info = line[4];
		        		
		        		NationalDestinationCode code = NationalDestinationCode.create()
		        				.setMaxDigits(maxDigits)
		        				.setMinDigits(minDigits)
		        				.setUsage(usage)
		        				.setInfo(info);
		        		
		        		Node node = countryNode.enter(prefix, 0);
		        		if (node._cityCode != null) {
		        			// Ambiguous city code
		        			
		        			node._city = join(node._city, info);
		        			node._info.setUsage(join(node._info.getUsage(), usage));
		        			node._info.setInfo(join(node._info.getInfo(), info));
		        		} else {
		        			node._cityCode = "0" + prefix;
		        			
		        			node._contryCode = countryNode.getCountryCode();
		        			node._countries = countryNode.getCountries();
		        			node._city = info;
		        			node._info = code;
		        		}
		        	}
				}				
			}
		} catch (CsvValidationException | IOException ex) {
			LOG.error("Failed to read phone prefix list.", ex);
		}
	}
	
	private static String join(String a, String b) {
		return a == null || a.isBlank() ? b : b == null || b.isBlank() ? a : a.equals(b) ? a : a + ", " + b;
	}

	private static PrefixInfo findInfo(String phone) {
		return PREFIX_TREE.find(phone, 1, PREFIX_TREE);
	}
	
	private interface PrefixInfo {
		/**
		 * The well-known number prefix.
		 */
		public String getCountryCode();

		/**
		 * The label for the {@link #getCountryCode()}.
		 */
		public List<Country> getCountries();
		
		/** 
		 * The local dial prefix.
		 */
		String getCityCode();
		
		/**
		 * The city or region. 
		 */
		String getCity();
		
		int getMinDigits();
		
		int getMaxDigits();
		
		String getUsage();
		
	}
	
	private static class Node implements PrefixInfo {
		
		@SuppressWarnings("unused")
		char _ch;
		
		String _contryCode;
		List<Country> _countries = Collections.emptyList();
		String _cityCode;
		String _city;
		NationalDestinationCode _info;
		Node[] _suffixes;
		
		/** 
		 * Creates a {@link Node}.
		 */
		public Node(char ch) {
			_ch = ch;
		}
		
		@Override
		public String getCountryCode() {
			return _contryCode;
		}
		
		@Override
		public List<Country> getCountries() {
			return _countries;
		}
		
		@Override
		public String getCityCode() {
			return _cityCode;
		}
		
		@Override
		public String getCity() {
			return _city;
		}
		
		@Override
		public int getMaxDigits() {
			return _info == null ? -1 : _info.getMaxDigits();
		}
		
		@Override
		public int getMinDigits() {
			return _info == null ? -1 : _info.getMinDigits();
		}
		
		@Override
		public String getUsage() {
			return _info == null ? null : _info.getUsage();
		}

		private Node enter(String prefix, int pos) {
			if (pos == prefix.length()) {
				return this;
			}
			
			char ch = prefix.charAt(pos);
			int index = ch - '0';
			
			if (_suffixes == null) {
				_suffixes = new Node[10];
			}
			
			Node suffix = _suffixes[index];
			if (suffix == null) {
				suffix = new Node(ch);
				_suffixes[index] = suffix;
			}
			
			return suffix.enter(prefix, pos + 1);
		}
		
		private Node find(String number, int pos, Node last) {
			Node result = _contryCode == null ? last : this;
			while (pos < number.length()) {
				char ch = number.charAt(pos);
				int index = ch - '0';
				if (index < 0 || index > 9) {
					// Skip char.
					pos++;
					continue;
				}
				
				if (_suffixes != null) {
					Node suffix = _suffixes[index];
					if (suffix != null) {
						return suffix.find(number, pos + 1, result);
					}
				}
				
				break;
			}
			return result;
		}
	}

	/**
	 * Creates a hash value of the given phone number.
	 * 
	 * <p>
	 * The hash value is used to provide a fast lookup for SPAM numbers when searching with advanced privacy enabled.
	 * </p>
	 */
	public static byte[] getPhoneHash(PhoneNumer number) {
		return getPhoneHash(PhoneHash.createPhoneDigest(), number);
	}
	
	public static byte[] getPhoneHash(MessageDigest digest, PhoneNumer number) {
		return PhoneHash.getPhoneHash(digest, number.getPlus());
	}
	
}
