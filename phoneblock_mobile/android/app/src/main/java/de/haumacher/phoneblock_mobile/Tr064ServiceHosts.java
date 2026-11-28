package de.haumacher.phoneblock_mobile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Derives the host addresses under which a Fritz!Box announced via mDNS
 * ({@code _tr064._tcp}) can be reached.
 *
 * <p>
 * A Fritz!Box announces itself with TXT attributes like
 * {@code ipv4=192.168.178.1}, {@code ipv6=fe80::...} and
 * {@code path=http://fritz.box:49000/tr64desc.xml}. Pure Java, without Android
 * classes, so that it can be unit tested.
 * </p>
 */
public final class Tr064ServiceHosts {

    private static final Pattern IPV4 = Pattern.compile(
        "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");

    private Tr064ServiceHosts() {
        // Static helper.
    }

    /**
     * The hosts to try for one announced service, best first: the IPv4 address from the TXT
     * attribute {@code ipv4}, the resolved IPv4 addresses, the resolved IPv6 addresses that are
     * not link-local (in brackets, ready for a URL), and finally the host name from the TXT
     * attribute {@code path}. Without duplicates.
     *
     * @param txt The TXT attributes of the service, may be {@code null}.
     * @param addresses The resolved addresses as returned by {@code InetAddress.getHostAddress()},
     *        may be {@code null}.
     */
    public static List<String> candidateHosts(Map<String, byte[]> txt, List<String> addresses) {
        Set<String> result = new LinkedHashSet<>();

        String ipv4Hint = attribute(txt, "ipv4");
        if (ipv4Hint != null && isIPv4(ipv4Hint)) {
            result.add(ipv4Hint);
        }

        List<String> ipv6 = new ArrayList<>();
        if (addresses != null) {
            for (String address : addresses) {
                if (address == null) {
                    continue;
                }
                String plain = stripZone(address.trim());
                if (isIPv4(plain)) {
                    result.add(plain);
                } else if (plain.indexOf(':') >= 0 && !isLinkLocal(plain)) {
                    ipv6.add("[" + plain + "]");
                }
            }
        }
        result.addAll(ipv6);

        String pathHost = hostOfUrl(attribute(txt, "path"));
        if (pathHost != null) {
            result.add(pathHost);
        }

        return new ArrayList<>(result);
    }

    /**
     * The value of a TXT attribute as trimmed text, or {@code null} if missing or empty.
     */
    static String attribute(Map<String, byte[]> txt, String key) {
        if (txt == null) {
            return null;
        }
        byte[] value = txt.get(key);
        if (value == null) {
            return null;
        }
        String text = new String(value, StandardCharsets.UTF_8).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * The host name of a URL like {@code http://fritz.box:49000/tr64desc.xml}, or {@code null}
     * if the URL cannot be parsed or names no host.
     */
    static String hostOfUrl(String url) {
        if (url == null) {
            return null;
        }
        try {
            String host = new URI(url).getHost();
            return (host == null || host.isEmpty()) ? null : host;
        } catch (Exception ex) {
            return null;
        }
    }

    static boolean isIPv4(String address) {
        return IPV4.matcher(address).matches();
    }

    private static boolean isLinkLocal(String ipv6) {
        return ipv6.toLowerCase().startsWith("fe80:");
    }

    private static String stripZone(String address) {
        int zone = address.indexOf('%');
        return zone < 0 ? address : address.substring(0, zone);
    }
}
