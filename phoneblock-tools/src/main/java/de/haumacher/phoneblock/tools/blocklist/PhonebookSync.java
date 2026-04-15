/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.tools.blocklist;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads a Fritz!Box phonebook export and lists all numbers that are not yet
 * present in the PhoneBlock blocklist.
 */
public class PhonebookSync {

	private static final String DEFAULT_API = "https://phoneblock.net/phoneblock/api/";

	public static void main(String[] args) throws Exception {
		String phonebookFile = null;
		String token = null;
		String prefix = "+49";
		String apiUrl = DEFAULT_API;
		boolean exact = false;
		int minVotes = 1;

		for (int i = 0; i < args.length; i++) {
			String a = args[i];
			switch (a) {
				case "--token", "-t" -> token = args[++i];
				case "--prefix", "-p" -> prefix = args[++i];
				case "--api" -> apiUrl = args[++i];
				case "--exact" -> exact = true;
				case "--minVotes", "-m" -> minVotes = Integer.parseInt(args[++i]);
				case "--help", "-h" -> { printUsage(); return; }
				default -> {
					if (i == args.length - 1 && !a.startsWith("-")) {
						phonebookFile = a;
					} else {
						System.err.println("Unknown argument: " + a);
						printUsage();
						System.exit(2);
					}
				}
			}
		}

		if (phonebookFile == null || token == null) {
			printUsage();
			System.exit(2);
		}

		List<String> phonebookNumbers = readPhonebookNumbers(phonebookFile);
		int wildcards = 0;
		Set<String> normalized = new LinkedHashSet<>();
		for (String n : phonebookNumbers) {
			if (n.contains("*")) {
				wildcards++;
				continue;
			}
			String e164 = toE164(n, prefix);
			if (e164 != null) {
				normalized.add(e164);
			}
		}

		Map<String, Integer> blocklist = fetchBlocklist(apiUrl, token);

		int onBlocklist = 0;
		int knownButBelowThreshold = 0;
		int invalid = 0;
		int whitelisted = 0;
		HttpClient client = exact ? HttpClient.newHttpClient() : null;
		List<String> missing = new ArrayList<>();
		for (String n : normalized) {
			Integer blVotes = blocklist.get(n);
			if (blVotes != null) {
				if (blVotes >= minVotes) {
					onBlocklist++;
				} else {
					knownButBelowThreshold++;
				}
				continue;
			}
			if (exact) {
				NumberInfo info = queryNumber(client, apiUrl, token, n);
				if (info.invalid) {
					invalid++;
					continue;
				}
				if (info.whitelisted) {
					whitelisted++;
					continue;
				}
				if (info.archived || info.votes > 0 || info.votesWildcard > 0) {
					if (info.votes >= minVotes) {
						onBlocklist++;
					} else {
						knownButBelowThreshold++;
					}
					continue;
				}
			}
			missing.add(n);
		}

		for (String n : missing) {
			System.out.println(n);
		}

		System.err.println();
		System.err.println("Statistik:");
		System.err.println("  Nummern gesamt:        " + phonebookNumbers.size());
		System.err.println("  Wildcards (ignoriert): " + wildcards);
		System.err.println("  Auf der Blocklist:     " + onBlocklist);
		System.err.println("  Bekannt (unter " + minVotes + " Votes): " + knownButBelowThreshold);
		if (exact) {
			System.err.println("  Whitelisted:           " + whitelisted);
			System.err.println("  Ungültige Nummern:     " + invalid);
		}
		System.err.println("  Nicht auf Blocklist:   " + missing.size());
	}

	private static void printUsage() {
		System.err.println("""
				Usage: phonebook-sync --token <api-token> [--prefix +49] [--api <url>]
				                     [--exact] [--minVotes N] <phonebook.xml>

				--exact     Queries each number that is missing from the blocklist download
				            via the /num/ API to include archived or below-threshold numbers.
				--minVotes  Treat blocklist entries with fewer votes as "below threshold"
				            (default: 1). Only entries with at least this many votes count
				            as actively blocked.

				Reads a Fritz!Box phonebook export and prints every number that is not yet
				on the PhoneBlock blocklist. Entries containing wildcards (*) are skipped.
				""");
	}

	static List<String> readPhonebookNumbers(String file) throws Exception {
		List<String> result = new ArrayList<>();
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		try (InputStream in = new FileInputStream(file)) {
			Document doc = dbf.newDocumentBuilder().parse(in);
			NodeList numbers = doc.getElementsByTagName("number");
			for (int i = 0; i < numbers.getLength(); i++) {
				Node node = numbers.item(i);
				if (node instanceof Element e) {
					String text = e.getTextContent();
					if (text != null) {
						text = text.trim();
						if (!text.isEmpty()) {
							result.add(text);
						}
					}
				}
			}
		}
		return result;
	}

	/**
	 * Normalizes a phonebook number to E.164 form matching the blocklist API.
	 *
	 * @param defaultPrefix international prefix used for national numbers (e.g. "+49")
	 * @return the normalized number, or {@code null} if the input cannot be parsed
	 */
	static String toE164(String raw, String defaultPrefix) {
		StringBuilder digits = new StringBuilder();
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c >= '0' && c <= '9') {
				digits.append(c);
			} else if (c == '+' && digits.length() == 0) {
				digits.append(c);
			}
		}
		String n = digits.toString();
		if (n.isEmpty()) {
			return null;
		}
		if (n.startsWith("+")) {
			return n;
		}
		if (n.startsWith("00")) {
			return "+" + n.substring(2);
		}
		if (n.startsWith("0")) {
			return defaultPrefix + n.substring(1);
		}
		return defaultPrefix + n;
	}

	static class NumberInfo {
		boolean invalid;
		boolean whitelisted;
		boolean archived;
		int votes;
		int votesWildcard;

		static NumberInfo unknown() { return new NumberInfo(); }
		static NumberInfo invalid() { NumberInfo i = new NumberInfo(); i.invalid = true; return i; }
	}

	static NumberInfo queryNumber(HttpClient client, String apiUrl, String token, String phone) throws Exception {
		String base = apiUrl.endsWith("/") ? apiUrl : apiUrl + "/";
		URI uri = URI.create(base + "num/" + phone + "?format=json");
		HttpRequest req = HttpRequest.newBuilder(uri)
				.header("Authorization", "Bearer " + token)
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
		if (resp.statusCode() == 404) {
			return NumberInfo.unknown();
		}
		if (resp.statusCode() == 400) {
			return NumberInfo.invalid();
		}
		if (resp.statusCode() / 100 != 2) {
			throw new RuntimeException("Query for " + phone + " failed: HTTP " + resp.statusCode()
					+ " - " + new String(resp.body(), StandardCharsets.UTF_8));
		}
		JsonNode json = new ObjectMapper().readTree(resp.body());
		NumberInfo info = new NumberInfo();
		info.whitelisted = json.path("whiteListed").asBoolean(false);
		info.archived = json.path("archived").asBoolean(false);
		info.votes = json.path("votes").asInt(0);
		info.votesWildcard = json.path("votesWildcard").asInt(0);
		return info;
	}

	static Map<String, Integer> fetchBlocklist(String apiUrl, String token) throws Exception {
		String base = apiUrl.endsWith("/") ? apiUrl : apiUrl + "/";
		URI uri = URI.create(base + "blocklist?format=json");
		HttpClient client = HttpClient.newHttpClient();
		HttpRequest req = HttpRequest.newBuilder(uri)
				.header("Authorization", "Bearer " + token)
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
		if (resp.statusCode() / 100 != 2) {
			throw new RuntimeException("Blocklist request failed: HTTP " + resp.statusCode()
					+ " - " + new String(resp.body(), StandardCharsets.UTF_8));
		}
		JsonNode root = new ObjectMapper().readTree(resp.body());
		JsonNode numbers = root.path("numbers");
		Map<String, Integer> result = new HashMap<>(Math.max(16, numbers.size() * 2));
		for (JsonNode entry : numbers) {
			int votes = entry.path("votes").asInt(0);
			if (votes <= 0) {
				continue;
			}
			String phone = entry.path("phone").asText(null);
			if (phone != null && !phone.isEmpty()) {
				result.put(phone, votes);
			}
		}
		return result;
	}

}
