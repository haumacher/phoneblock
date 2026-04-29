package de.haumacher.phoneblock.util;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Validates callback URLs that point back at a device on the user's
 * own LAN. Used by flows where the server hands a credential or
 * identity assertion to a client running on a private-IP host
 * (PhoneBlock dongle, future on-prem integrations) — we must refuse
 * any callback that could leak the value to the public internet.
 */
public final class LoopbackCallbacks {

	private LoopbackCallbacks() {
		// no instances
	}

	/**
	 * Returns the (possibly normalized) URL on success, {@code null}
	 * if it should be refused.
	 *
	 * <p>
	 * Accepted: plain {@code http://} scheme, and a host that is
	 * either a private-range IPv4 address, a hostname ending in
	 * {@code .fritz.box} or {@code .local}, or any single-label name
	 * (no dots, e.g. {@code answerbot}, {@code localhost}, or a
	 * user-renamed dongle). Single-label names cannot be resolved by
	 * public DNS — they only ever map to a host on the local network
	 * via mDNS, NetBIOS, or the LAN resolver — so they're safe to
	 * treat as loopback. Everything else — public hostnames, odd
	 * schemes, userinfo, fragments — is refused.
	 */
	public static String validate(String raw) {
		if (raw == null || raw.isBlank()) return null;
		URI uri;
		try {
			uri = new URI(raw);
		} catch (URISyntaxException e) {
			return null;
		}
		if (!"http".equalsIgnoreCase(uri.getScheme())) return null;
		if (uri.getUserInfo() != null) return null;
		if (uri.getFragment() != null) return null;
		String host = uri.getHost();
		if (host == null) return null;
		String h = host.toLowerCase();
		boolean hostOk =
			!h.contains(".") ||
			h.endsWith(".fritz.box") ||
			h.endsWith(".local") ||
			isPrivateIp(h);
		if (!hostOk) return null;
		return uri.toString();
	}

	private static boolean isPrivateIp(String host) {
		String[] parts = host.split("\\.");
		if (parts.length != 4) return false;
		int[] o = new int[4];
		for (int i = 0; i < 4; i++) {
			try {
				o[i] = Integer.parseInt(parts[i]);
			} catch (NumberFormatException e) {
				return false;
			}
			if (o[i] < 0 || o[i] > 255) return false;
		}
		if (o[0] == 10) return true;
		if (o[0] == 127) return true;
		if (o[0] == 169 && o[1] == 254) return true;
		if (o[0] == 172 && o[1] >= 16 && o[1] <= 31) return true;
		if (o[0] == 192 && o[1] == 168) return true;
		return false;
	}
}
