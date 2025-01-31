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
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.ICSVParser;
import com.opencsv.exceptions.CsvValidationException;

import de.haumacher.phoneblock.app.api.model.PhoneNumer;

/**
 * Utility for analyzing phone numbers.
 */
public class NumberAnalyzer {

	private static final Logger LOG = LoggerFactory.getLogger(NumberAnalyzer.class);

	/**
	 * @see "https://de.wikipedia.org/wiki/L%C3%A4ndervorwahlliste_sortiert_nach_Nummern"
	 */
	private static final String[][] PREFIXES = { 
		{"+1", "Vereinigte Staaten oder Kanada"},
		{"+1242", "Bahamas"},
		{"+1246", "Barbados"},
		{"+1264", "Anguilla"},
		{"+1268", "Antigua und Barbuda"},
		{"+1284", "Britische Jungferninseln"},
		{"+1340", "Amerikanische Jungferninseln"},
		{"+1345", "Kaimaninseln"},
		{"+1441", "Bermuda"},
		{"+1473", "Grenada"},
		{"+1649", "Turks- und Caicosinseln"},
		{"+1664", "Montserrat"},
		{"+1670", "Nördliche Marianen"},
		{"+1671", "Guam"},
		{"+1684", "Amerikanisch-Samoa"},
		{"+1721", "Sint Maarten"},
		{"+1758", "St. Lucia"},
		{"+1767", "Dominica"},
		{"+1784", "St. Vincent und die Grenadinen"},
		{"+1787", "Puerto Rico"},
		{"+1808", "Hawaii"},
		{"+1809", "Dominikanische Republik"},
		{"+1829", "Dominikanische Republik"},
		{"+1849", "Dominikanische Republik"},
		{"+1868", "Trinidad und Tobago"},
		{"+1869", "St. Kitts und Nevis"},
		{"+1876", "Jamaika"},
		{"+1939", "Puerto Rico"},
		{"+20", "Ägypten"},
		{"+211", "Südsudan"},
		{"+212", "Marokko"},
		{"+213", "Algerien"},
		{"+216", "Tunesien"},
		{"+218", "Libyen"},
		{"+220", "Gambia"},
		{"+221", "Senegal"},
		{"+222", "Mauretanien"},
		{"+223", "Mali"},
		{"+224", "Guinea"},
		{"+225", "Elfenbeinküste"},
		{"+226", "Burkina Faso"},
		{"+227", "Niger"},
		{"+228", "Togo"},
		{"+229", "Benin"},
		{"+230", "Mauritius"},
		{"+231", "Liberia"},
		{"+232", "Sierra Leone"},
		{"+233", "Ghana"},
		{"+234", "Nigeria"},
		{"+235", "Tschad"},
		{"+236", "Zentralafrikanische Republik"},
		{"+237", "Kamerun"},
		{"+238", "Kap Verde"},
		{"+239", "São Tomé und Príncipe"},
		{"+240", "Äquatorialguinea"},
		{"+241", "Gabun"},
		{"+242", "Republik Kongo"},
		{"+243", "Demokratische Republik Kongo"},
		{"+244", "Angola"},
		{"+245", "Guinea-Bissau"},
		{"+246", "Britisches Territorium im Indischen Ozean"},
		{"+247", "Ascension"},
		{"+248", "Seychellen"},
		{"+249", "Sudan"},
		{"+250", "Ruanda"},
		{"+251", "Äthiopien"},
		{"+252", "Somalia"},
		{"+253", "Dschibuti"},
		{"+254", "Kenia"},
		{"+255", "Tansania"},
		{"+256", "Uganda"},
		{"+257", "Burundi"},
		{"+258", "Mosambik"},
		{"+260", "Sambia"},
		{"+261", "Madagaskar"},
		{"+262", "Französische Gebiete im Indischen Ozean, darunter  Réunion,  Mayotte"},
		{"+263", "Simbabwe"},
		{"+264", "Namibia"},
		{"+265", "Malawi"},
		{"+266", "Lesotho"},
		{"+267", "Botswana"},
		{"+268", "Eswatini"},
		{"+269", "Komoren"},
		{"+27", "Südafrika"},
		{"+290", "St. Helena"},
		{"+2908", "Tristan da Cunha"},
		{"+291", "Eritrea"},
		{"+297", "Aruba"},
		{"+298", "Färöer"},
		{"+299", "Grönland"},
		{"+30", "Griechenland"},
		{"+31", "Niederlande"},
		{"+32", "Belgien"},
		{"+33", "Frankreich"},
		{"+34", "Spanien"},
		{"+350", "Gibraltar"},
		{"+351", "Portugal"},
		{"+352", "Luxemburg"},
		{"+353", "Irland"},
		{"+354", "Island"},
		{"+355", "Albanien"},
		{"+356", "Malta"},
		{"+357", "Zypern"},
		{"+358", "Finnland"},
		{"+359", "Bulgarien"},
		{"+36", "Ungarn"},
		{"+370", "Litauen"},
		{"+371", "Lettland"},
		{"+372", "Estland"},
		{"+373", "Moldau"},
		{"+374", "Armenien einschließlich  Arzach"},
		{"+375", "Belarus"},
		{"+376", "Andorra"},
		{"+377", "Monaco"},
		{"+378", "San Marino"},
		{"+379", "Vatikanstadt"},
		{"+380", "Ukraine"},
		{"+381", "Serbien"},
		{"+382", "Montenegro"},
		{"+383", "Kosovo"},
		{"+385", "Kroatien"},
		{"+386", "Slowenien"},
		{"+387", "Bosnien und Herzegowina"},
		{"+389", "Nordmazedonien"},
		{"+39", "Italien"},
		{"+3906", "Vatikanstadt"},
		{"+40", "Rumänien"},
		{"+41", "Schweiz"},
		{"+420", "Tschechien"},
		{"+421", "Slowakei"},
		{"+423", "Liechtenstein"},
		{"+43", "Österreich"},
		{"+44", "Vereinigtes Königreich"},
		{"+45", "Dänemark"},
		{"+46", "Schweden"},
		{"+47", "Norwegen"},
		{"+48", "Polen"},
		{"+49", "Deutschland"},
		{"+500", "Falklandinseln"},
		{"+501", "Belize"},
		{"+502", "Guatemala"},
		{"+503", "El Salvador"},
		{"+504", "Honduras"},
		{"+505", "Nicaragua"},
		{"+506", "Costa Rica"},
		{"+507", "Panama"},
		{"+508", "Saint-Pierre und Miquelon"},
		{"+509", "Haiti"},
		{"+51", "Peru"},
		{"+52", "Mexiko"},
		{"+53", "Kuba"},
		{"+54", "Argentinien"},
		{"+55", "Brasilien"},
		{"+56", "Chile"},
		{"+57", "Kolumbien"},
		{"+58", "Venezuela"},
		{"+590", "Guadeloupe,  St. Martin,  Saint-Barthélemy"},
		{"+591", "Bolivien"},
		{"+592", "Guyana"},
		{"+593", "Ecuador"},
		{"+594", "Französisch-Guayana"},
		{"+595", "Paraguay"},
		{"+596", "Martinique"},
		{"+597", "Suriname"},
		{"+598", "Uruguay"},
		{"+599", "Bonaire,  Curaçao, Saba und Sint Eustatius"},
		{"+60", "Malaysia"},
		{"+61", "Australien"},
		{"+62", "Indonesien"},
		{"+63", "Philippinen"},
		{"+64", "Neuseeland"},
		{"+65", "Singapur"},
		{"+66", "Thailand"},
		{"+670", "Osttimor"},
		{"+672", "Australische Außengebiete: Antarktis,  Norfolkinsel"},
		{"+673", "Brunei"},
		{"+674", "Nauru"},
		{"+675", "Papua-Neuguinea"},
		{"+676", "Tonga"},
		{"+677", "Salomonen"},
		{"+678", "Vanuatu"},
		{"+679", "Fidschi"},
		{"+680", "Palau"},
		{"+681", "Wallis und Futuna"},
		{"+682", "Cookinseln"},
		{"+683", "Niue"},
		{"+685", "Samoa"},
		{"+686", "Kiribati, Gilbertinseln"},
		{"+687", "Neukaledonien"},
		{"+688", "Tuvalu, Elliceinseln"},
		{"+689", "Französisch-Polynesien"},
		{"+690", "Tokelau"},
		{"+691", "Mikronesien"},
		{"+692", "Marshallinseln"},
		{"+7", "Russland"},
		{"+7840", "Abchasien"},
		{"+7940", "Abchasien"},
		{"+800", "Internationale Free-Phone-Dienste"},
		{"+808", "Internationale Service-Dienste"},
		{"+81", "Japan"},
		{"+82", "Südkorea"},
		{"+84", "Vietnam"},
		{"+850", "Nordkorea"},
		{"+852", "Hongkong"},
		{"+853", "Macau"},
		{"+855", "Kambodscha"},
		{"+856", "Laos"},
		{"+86", "Volksrepublik China"},
		{"+870", "Inmarsat Single Number Access"},
		{"+878", "Persönliche Rufnummern"},
		{"+880", "Bangladesch"},
		{"+881", "Globales mobiles Satellitensystem"},
		{"+882", "Internationale Netzwerke"},
		{"+883", "Internationale Netzwerke"},
		{"+886", "Taiwan"},
		{"+888", "OCHA, für Telecommunications for Disaster Relief"},
		{"+90", "Türkei,  Türkische Republik Nordzypern"},
		{"+91", "Indien"},
		{"+92", "Pakistan"},
		{"+93", "Afghanistan"},
		{"+94", "Sri Lanka"},
		{"+95", "Myanmar"},
		{"+960", "Malediven"},
		{"+961", "Libanon"},
		{"+962", "Jordanien"},
		{"+963", "Syrien"},
		{"+964", "Irak"},
		{"+965", "Kuwait"},
		{"+966", "Saudi-Arabien"},
		{"+967", "Jemen"},
		{"+968", "Oman"},
		{"+970", "Palästina"},
		{"+971", "Vereinigte Arabische Emirate"},
		{"+972", "Israel"},
		{"+973", "Bahrain"},
		{"+974", "Katar"},
		{"+975", "Bhutan"},
		{"+976", "Mongolei"},
		{"+977", "Nepal"},
		{"+979", "Internationale Premium-Rate-Dienste"},
		{"+98", "Iran"},
		{"+991", "International Telecommunications Public Correspondence Service Trials"},
		{"+992", "Tadschikistan"},
		{"+993", "Turkmenistan"},
		{"+994", "Aserbaidschan"},
		{"+995", "Georgien"},
		{"+996", "Kirgisistan"},
		{"+998", "Usbekistan"},
	};

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
		
		String plus;
		if (phone.startsWith("00")) {
			plus = "+" + phone.substring(2);
		} else if (phone.startsWith("0")) {
			plus = "+49" + phone.substring(1);
		} else if (phone.startsWith("+")) {
			plus = phone;
		} else {
			// No valid number.
			return null;
		}
		result.setPlus(plus);
		result.setZeroZero("00" + plus.substring(1));
		
		if (plus.startsWith("+49")) {
			result.setShortcut("0" + plus.substring(3));
		}
		
		PrefixInfo info = findInfo(plus);
		result.setCountryCode(info.getCountryCode());
		result.setCountry(info.getCountry());
		result.setCityCode(info.getCityCode());
		result.setCity(info.getCity());
	
		return result;
	}
	
	/**
	 * Creates an database ID for the given analyzed {@link PhoneNumer}.
	 */
	public static String getPhoneId(PhoneNumer number) {
		String shortcut = number.getShortcut();
		return shortcut == null ? number.getZeroZero() : shortcut;
	}

	private static Node buildTree() {
		Node root = new Node('+');
		root._country = "Unbekannt";
		for (String[] entry : PREFIXES) {
			Node node = root.enter(entry[0], 1);
			assert node._contryCode == null : "Anbiguous country code: " + node._contryCode;
			node._contryCode = entry[0];
			node._country = entry[1];
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
		        		node._country = germany.getCountry();
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
		public String getCountry();
		
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
		String _country;
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
		public String getCountry() {
			return _country;
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
		return getPhoneHash(createPhoneDigest(), number);
	}
	
	public static byte[] getPhoneHash(MessageDigest digest, PhoneNumer number) {
		return digest.digest(number.getPlus().getBytes(StandardCharsets.UTF_8));
	}

	public static MessageDigest createPhoneDigest() {
		try {
			return MessageDigest.getInstance("SHA1");
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("Cannot hash phone number.", ex);
		}
	}
	
}
