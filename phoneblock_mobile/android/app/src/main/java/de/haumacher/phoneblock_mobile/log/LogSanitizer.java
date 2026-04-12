package de.haumacher.phoneblock_mobile.log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Helpers for sanitising values before they hit the diagnostic log.
 */
public final class LogSanitizer {

    private LogSanitizer() {}

    /**
     * Returns a short, non-reversible marker for a phone number so that
     * call flows can be correlated in the log without leaking the raw number.
     *
     * @param phone The raw phone number, may be null.
     * @return A string of the form {@code sha1:xxxxxxxx}. Returns {@code sha1:-}
     *         when {@code phone} is null.
     */
    public static String hashPhone(String phone) {
        if (phone == null) {
            return "sha1:-";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(phone.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("sha1:");
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "sha1:?";
        }
    }
}
