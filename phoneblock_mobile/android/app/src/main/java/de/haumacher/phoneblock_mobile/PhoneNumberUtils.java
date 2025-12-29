package de.haumacher.phoneblock_mobile;

import androidx.annotation.NonNull;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for phone number normalization and hashing.
 * Provides privacy-preserving phone number operations.
 */
public class PhoneNumberUtils {

    /**
     * Normalizes a phone number to E.164 international format.
     * This ensures consistent hashing regardless of how the number is formatted.
     *
     * @param rawNumber Phone number in any format
     * @param countryCode ISO country code (e.g., "DE", "US") for parsing context
     * @return Phone number in E.164 format (e.g., +4917650642602) or null if parsing fails
     */
    public static String normalizeToInternationalFormat(String rawNumber, String countryCode) {
        try {
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

            // Use provided country code, default to DE if not specified
            String region = (countryCode != null && !countryCode.isEmpty()) ? countryCode : "DE";

            // Parse the number
            Phonenumber.PhoneNumber phoneNumber = phoneUtil.parse(rawNumber, region.toUpperCase());

            // Validate that it's a possible number
            if (!phoneUtil.isPossibleNumber(phoneNumber)) {
                return null;
            }

            // Format in E.164 international format (e.g., +4917650642602)
            return phoneUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            return null;
        }
    }

    /**
     * Computes the SHA1 hash of a phone number in international format.
     * This preserves user privacy by not transmitting the actual phone number.
     *
     * @param number Phone number in international format (e.g., +4917650642602)
     * @return 40-character hex-encoded SHA1 hash (uppercase)
     * @throws IllegalArgumentException if SHA-1 algorithm is not available
     */
    public static @NonNull String computeSHA1(String number) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(number.getBytes(StandardCharsets.UTF_8));

            // Convert to uppercase hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 is always available in Android
            throw new IllegalArgumentException("SHA-1 algorithm not available", e);
        }
    }

    /**
     * Normalizes a phone number and computes its SHA1 hash in one operation.
     * This is the complete privacy-preserving transformation.
     *
     * @param rawNumber Phone number in any format
     * @param countryCode ISO country code for parsing context
     * @return SHA1 hash of the normalized number, or null if normalization fails
     */
    public static String normalizeAndHash(String rawNumber, String countryCode) {
        String normalized = normalizeToInternationalFormat(rawNumber, countryCode);
        if (normalized == null) {
            return null;
        }
        return computeSHA1(normalized);
    }
}
