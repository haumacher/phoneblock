package de.haumacher.phoneblock_mobile;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for PhoneNumberUtils class.
 * Tests phone number normalization to E.164 format and SHA-1 hashing.
 */
public class PhoneNumberUtilsTest {

    // Test normalization of German phone numbers
    @Test
    public void testNormalizeGermanMobileNumber_WithCountryCode() {
        String result = PhoneNumberUtils.normalizeToInternationalFormat("+4917650642602", "DE");
        assertEquals("+4917650642602", result);
    }

    @Test
    public void testNormalizeGermanMobileNumber_NationalFormat() {
        String result = PhoneNumberUtils.normalizeToInternationalFormat("017650642602", "DE");
        assertEquals("+4917650642602", result);
    }

    @Test
    public void testNormalizeGermanMobileNumber_WithSpaces() {
        String result = PhoneNumberUtils.normalizeToInternationalFormat("+49 176 50642602", "DE");
        assertEquals("+4917650642602", result);
    }

    @Test
    public void testNormalizeGermanMobileNumber_WithDashes() {
        String result = PhoneNumberUtils.normalizeToInternationalFormat("+49-176-50642602", "DE");
        assertEquals("+4917650642602", result);
    }

    @Test
    public void testNormalizeGermanLandlineNumber() {
        String result = PhoneNumberUtils.normalizeToInternationalFormat("030123456", "DE");
        assertEquals("+4930123456", result);
    }

    @Test
    public void testNormalizeGermanLandlineNumber_WithAreaCode() {
        String result = PhoneNumberUtils.normalizeToInternationalFormat("+49 30 123456", "DE");
        assertEquals("+4930123456", result);
    }

    // Test normalization with different country codes
    @Test
    public void testNormalizeUSNumber() {
        String result = PhoneNumberUtils.normalizeToInternationalFormat("2025550123", "US");
        assertEquals("+12025550123", result);
    }

    @Test
    public void testNormalizeUSNumber_WithCountryCode() {
        String result = PhoneNumberUtils.normalizeToInternationalFormat("+1 202 555 0123", "US");
        assertEquals("+12025550123", result);
    }

    @Test
    public void testNormalizeFrenchNumber() {
        String result = PhoneNumberUtils.normalizeToInternationalFormat("0612345678", "FR");
        assertEquals("+33612345678", result);
    }

    @Test
    public void testNormalizeSwissNumber() {
        String result = PhoneNumberUtils.normalizeToInternationalFormat("0791234567", "CH");
        assertEquals("+41791234567", result);
    }

    // Test default fallback to DE when country code is null or empty
    @Test
    public void testNormalizeWithNullCountryCode_DefaultsToDE() {
        String result = PhoneNumberUtils.normalizeToInternationalFormat("017650642602", null);
        assertEquals("+4917650642602", result);
    }

    @Test
    public void testNormalizeWithEmptyCountryCode_DefaultsToDE() {
        String result = PhoneNumberUtils.normalizeToInternationalFormat("017650642602", "");
        assertEquals("+4917650642602", result);
    }

    // Test various edge case numbers
    @Test
    public void testNormalizeShortNumber() {
        // libphonenumber accepts short numbers if they parse successfully
        String result = PhoneNumberUtils.normalizeToInternationalFormat("123", "DE");
        // Short numbers that parse successfully get formatted
        assertNotNull(result);
    }

    @Test
    public void testNormalizeInvalidNumber_TooLong() {
        String result = PhoneNumberUtils.normalizeToInternationalFormat("01765064260201234567890", "DE");
        // Extremely long numbers fail to parse
        assertNull(result);
    }

    @Test
    public void testNormalizeNumberWithLetters() {
        // libphonenumber interprets letters as phone keypad numbers (A=2, B=2, C=2)
        String result = PhoneNumberUtils.normalizeToInternationalFormat("0176ABC", "DE");
        // Letters are converted to their numeric keypad equivalents
        assertEquals("+49176222", result);
    }

    @Test
    public void testNormalizeGermanLandline_WithLeadingZero() {
        // Test the specific number from the logs: 022376922894
        String result = PhoneNumberUtils.normalizeToInternationalFormat("022376922894", "DE");
        assertEquals("+4922376922894", result);
    }

    @Test
    public void testNormalizeInvalidNumber_Empty() {
        String result = PhoneNumberUtils.normalizeToInternationalFormat("", "DE");
        assertNull(result);
    }

    @Test
    public void testNormalizeInvalidNumber_OnlySpaces() {
        String result = PhoneNumberUtils.normalizeToInternationalFormat("   ", "DE");
        assertNull(result);
    }

    // Test SHA-1 hash computation
    @Test
    public void testComputeSHA1_KnownValue() {
        // Test with the example from the PhoneBlock documentation
        String hash = PhoneNumberUtils.computeSHA1("+4917650642602");
        assertEquals("3D1D76F0C3664E1E818C6ECCFD8843AD1F4091CC", hash);
    }

    @Test
    public void testComputeSHA1_UpperCase() {
        // Verify hash is always uppercase
        String hash = PhoneNumberUtils.computeSHA1("+4917650642602");
        assertTrue(hash.matches("[0-9A-F]+"));
        assertEquals(40, hash.length()); // SHA-1 produces 40 hex characters
    }

    @Test
    public void testComputeSHA1_DifferentNumbers() {
        String hash1 = PhoneNumberUtils.computeSHA1("+4917650642602");
        String hash2 = PhoneNumberUtils.computeSHA1("+4917650642603");
        assertNotEquals(hash1, hash2);
    }

    @Test
    public void testComputeSHA1_SameNumberSameHash() {
        String hash1 = PhoneNumberUtils.computeSHA1("+4917650642602");
        String hash2 = PhoneNumberUtils.computeSHA1("+4917650642602");
        assertEquals(hash1, hash2);
    }

    @Test
    public void testComputeSHA1_EmptyString() {
        // SHA-1 of empty string is a known value
        String hash = PhoneNumberUtils.computeSHA1("");
        assertEquals("DA39A3EE5E6B4B0D3255BFEF95601890AFD80709", hash);
    }

    // Test the combined normalizeAndHash method
    @Test
    public void testNormalizeAndHash_ValidGermanNumber() {
        String hash = PhoneNumberUtils.normalizeAndHash("017650642602", "DE");
        assertEquals("3D1D76F0C3664E1E818C6ECCFD8843AD1F4091CC", hash);
    }

    @Test
    public void testNormalizeAndHash_AlreadyNormalized() {
        String hash = PhoneNumberUtils.normalizeAndHash("+4917650642602", "DE");
        assertEquals("3D1D76F0C3664E1E818C6ECCFD8843AD1F4091CC", hash);
    }

    @Test
    public void testNormalizeAndHash_InvalidNumber() {
        String hash = PhoneNumberUtils.normalizeAndHash("invalid", "DE");
        assertNull(hash);
    }

    @Test
    public void testNormalizeAndHash_NullCountryCode() {
        String hash = PhoneNumberUtils.normalizeAndHash("017650642602", null);
        assertEquals("3D1D76F0C3664E1E818C6ECCFD8843AD1F4091CC", hash);
    }

    // Test that different formats of the same number produce the same hash
    @Test
    public void testConsistentHashing_DifferentFormats() {
        String hash1 = PhoneNumberUtils.normalizeAndHash("017650642602", "DE");
        String hash2 = PhoneNumberUtils.normalizeAndHash("+4917650642602", "DE");
        String hash3 = PhoneNumberUtils.normalizeAndHash("+49 176 50642602", "DE");
        String hash4 = PhoneNumberUtils.normalizeAndHash("+49-176-50642602", "DE");

        assertEquals(hash1, hash2);
        assertEquals(hash2, hash3);
        assertEquals(hash3, hash4);
    }

    // Test edge cases
    @Test
    public void testNormalizeAndHash_NullInput() {
        String hash = PhoneNumberUtils.normalizeAndHash(null, "DE");
        assertNull(hash);
    }

    @Test
    public void testNormalizeToInternationalFormat_NullInput() {
        String result = PhoneNumberUtils.normalizeToInternationalFormat(null, "DE");
        assertNull(result);
    }

    // Test international numbers with correct country context
    @Test
    public void testNormalizeGermanNumberInUSContext() {
        // A German number formatted with +49 should work even in US context
        String result = PhoneNumberUtils.normalizeToInternationalFormat("+4917650642602", "US");
        assertEquals("+4917650642602", result);
    }

    @Test
    public void testNormalizeLocalNumberWithWrongContext() {
        // A German local number (without country code) in US context should fail or give wrong result
        String result = PhoneNumberUtils.normalizeToInternationalFormat("017650642602", "US");
        // This should either be null or give a US number, definitely not a German number
        assertNotEquals("+4917650642602", result);
    }

    // Test special characters in phone numbers
    @Test
    public void testNormalizeWithParentheses() {
        String result = PhoneNumberUtils.normalizeToInternationalFormat("(030) 123456", "DE");
        assertEquals("+4930123456", result);
    }

    @Test
    public void testNormalizeWithSlashes() {
        String result = PhoneNumberUtils.normalizeToInternationalFormat("030/123456", "DE");
        assertEquals("+4930123456", result);
    }

    @Test
    public void testNormalizeWithMixedSeparators() {
        String result = PhoneNumberUtils.normalizeToInternationalFormat("+49 (176) 506-426-02", "DE");
        assertEquals("+4917650642602", result);
    }
}
