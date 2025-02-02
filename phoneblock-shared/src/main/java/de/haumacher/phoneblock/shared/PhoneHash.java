package de.haumacher.phoneblock.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Algorithm creating SHA1 hashes of phone numbers compatible with the SPAM check API.
 */
public class PhoneHash {

	public static String toInternationalForm(String phone) {
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
