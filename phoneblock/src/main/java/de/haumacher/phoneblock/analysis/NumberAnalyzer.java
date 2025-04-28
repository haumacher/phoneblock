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

import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.location.Countries;
import de.haumacher.phoneblock.location.model.Country;
import de.haumacher.phoneblock.shared.PhoneHash;

/**
 * Utility for analyzing phone numbers.
 */
public class NumberAnalyzer {

	private static final Logger LOG = LoggerFactory.getLogger(NumberAnalyzer.class);

	private static Node PREFIX_TREE = buildTree();

	/** 
	 * Creates a database ID for the given phone number, or <code>null</code> if the number is invalid. 
	 */
	public static String toId(String phoneText) {
		PhoneNumer number = parsePhoneNumber(phoneText);
		return number == null ? null : getPhoneId(number);
	}

	public static PhoneNumer parsePhoneNumber(String phoneText) {
		String phoneNumber = normalizeNumber(phoneText);
		if (phoneNumber.contains("*")) {
			LOG.warn("Ignoring number with wildcard: " + phoneText);
			return null;
		}
		
		return analyze(phoneNumber);
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

	/**
	 * Analyzes the given (normalized) phone number.
	 */
	public static PhoneNumer analyze(String phone) {
		PhoneNumer result = PhoneNumer.create();
		
		String plus = PhoneHash.toInternationalForm(phone);
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
		
		result.setCountryCode(countryCode);
		List<Country> countries = info.getCountries();
		result.setCityCode(info.getCityCode());
		result.setCity(info.getCity());
		
		if (countries.isEmpty()) {
			// There is no shortcut.
			result.setCountry("Unknown");
			result.setShortcut(plus);
			result.setId(zeroZero);
		} else {
			result.setCountry(countries.stream().map(c -> c.getOfficialNameEn()).collect(Collectors.joining(", ")));
			String national = "0" + plus.substring(countryCode.length());
			result.setShortcut("(" + countries.stream().map(c -> c.getISO31661Alpha2()).collect(Collectors.joining(", ")) + ") " + national);
			if ("+49".equals(countryCode)) {
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
		
		// See https://www.bundesnetzagentur.de/SharedDocs/Downloads/DE/Sachgebiete/Telekommunikation/Unternehmen_Institutionen/Nummerierung/Rufnummern/ONRufnr/Vorwahlverzeichnis_ONB.zip.html
		try (InputStream in = NumberAnalyzer.class.getResourceAsStream("NVONB.INTERNET.20220727.ONB.csv")) {
			try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				Node germany = root.find("+49", 1, root);
				
				ICSVParser parser = new CSVParserBuilder().withSeparator(';').withStrictQuotes(false).build();
		        try (CSVReader csv = new CSVReaderBuilder(r).withCSVParser(parser).build()) {
		        	// Skip header.
		        	csv.readNext();
		        	while (true) {
		        		String[] line = csv.readNext();
		        		if (line == null || line.length < 3) {
		        			break;
		        		}
		        		
		        		if (!"1".equals(line[2])) {
		        			continue;
		        		}
		        		
		        		String cityCode = line[0];
		        		String city = line[1];
		        		
		        		Node node = germany.enter(cityCode, 0);
		        		assert node._cityCode == null : "Anbiguous city code: " + node._cityCode;
		        		node._contryCode = germany.getCountryCode();
		        		node._countries = germany.getCountries();
		        		node._cityCode = "0" + cityCode;
		        		node._city = city;
		        	}
				}				
			}
		} catch (CsvValidationException | IOException ex) {
			LOG.error("Failed to read phone prefix list.", ex);
		}
		
		return root;
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
		
	}
	
	private static class Node implements PrefixInfo {
		
		@SuppressWarnings("unused")
		char _ch;
		
		String _contryCode;
		List<Country> _countries = Collections.emptyList();
		String _cityCode;
		String _city;
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
