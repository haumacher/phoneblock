package de.haumacher.phoneblock.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Algorithm creating SHA1 hashes of phone numbers compatible with the SPAM check API.
 */
public class PhoneHash {

	public static String toInternationalForm(String phone) {
		return toInternationalForm(phone, "+49");
	}
	
	public static String toInternationalForm(String phone, String dialPrefix) {
		return toInternationalForm(phone, dialPrefix, null);
	}

	/**
	 * Converts a phone number to international format, taking into account country-specific trunk prefixes.
	 *
	 * @param phone The phone number to convert
	 * @param dialPrefix The international dial prefix for the user's country (e.g., "+49")
	 * @param trunkPrefixes List of trunk prefixes for the user's country (e.g., ["0"] for Germany, ["06"] for Hungary, ["8"] for Russia).
	 *                      If null or empty, assumes trunk prefix "0" for backward compatibility.
	 * @return The phone number in international format (starting with "+"), or null if invalid
	 */
	public static String toInternationalForm(String phone, String dialPrefix, java.util.List<String> trunkPrefixes) {
		String plus = null;

		if (phone.startsWith("00")) {
			if (phone.startsWith("000")) {
				// Not a phone number.
				return null;
			}
			plus = "+" + phone.substring(2);
		} else if (phone.startsWith("+")) {
			plus = phone;
		} else if (trunkPrefixes != null && !trunkPrefixes.isEmpty()) {
			// Try to match trunk prefix
			// Sort by length descending to match longest prefix first (greedy matching)
			java.util.List<String> sortedPrefixes = new java.util.ArrayList<>(trunkPrefixes);
			sortedPrefixes.sort((a, b) -> Integer.compare(b.length(), a.length()));

			for (String trunkPrefix : sortedPrefixes) {
				if (trunkPrefix.isEmpty()) {
					// Country has no trunk prefix, "0" is part of the area code
					plus = dialPrefix + phone;
					break;
				} else if (phone.startsWith(trunkPrefix)) {
					plus = dialPrefix + phone.substring(trunkPrefix.length());
					break;
				}
			}

			if (plus == null) {
				// Doesn't start with any valid trunk prefix
				return null;
			}
		} else {
			// Backward compatibility: assume trunk prefix is "0"
			if (phone.startsWith("0")) {
				plus = dialPrefix + phone.substring(1);
			} else {
				// No valid number.
				return null;
			}
		}

		if (plus == null || plus.length() <= 8) {
			// Most likely no valid phone number.
			return null;
		}

		return plus;
	}

	public static byte[] getPhoneHash(MessageDigest digest, String internationalForm) {
		return digest.digest(internationalForm.getBytes(StandardCharsets.UTF_8));
	}

	public static MessageDigest createPhoneDigest() {
		try {
			return MessageDigest.getInstance("SHA1");
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("Cannot hash phone number.", ex);
		}
	}

	public static char hex(int digit) {
		return "0123456789ABCDEF".charAt(digit);
	}

	public static String encodeHash(byte[] hash) {
		StringBuilder result = new StringBuilder();
		for (int n = 0; n < hash.length; n++) {
			char msb = hex((hash[n] >> 4) & 0x0F);
			char lsb = hex(hash[n] & 0x0F);
			result.append(msb);
			result.append(lsb);
		}
	
		String encodedForm = result.toString();
		return encodedForm;
	}

}
