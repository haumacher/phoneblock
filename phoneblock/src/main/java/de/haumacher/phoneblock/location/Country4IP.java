package de.haumacher.phoneblock.location;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;

import com.ip2location.IP2Location;
import com.ip2location.IPResult;

/**
 * Algorithm to retrieve the country, an IP address belongs to.
 * 
 * <p>
 * The lookup relies on data provided by https://ip2location.com/.
 * </p>
 * 
 * @see "https://download.ip2location.com/lite/IP2LOCATION-LITE-DB1.BIN.ZIP"
 * @see "https://download.ip2location.com/lite/IP2LOCATION-LITE-DB1.IPV6.BIN.ZIP"
 */
public class Country4IP {
    private final IP2Location ip4 = new IP2Location();
    private final IP2Location ip6 = new IP2Location();
    
    public Country4IP(String ip4db, String ip6db) throws IOException {
    	ip4.Open(ip4db);
    	ip6.Open(ip6db);
	}

    public String lookup(InetAddress address) throws IOException {
    	if (address instanceof Inet6Address) {
    		return ip6.IPQuery(address.getHostAddress()).getCountryShort();
    	} else {
    		return ip4.IPQuery(address.getHostAddress()).getCountryShort();
    	}
    }

    public String lookup(String address) throws IOException {
		IPResult ip4Result = ip4.IPQuery(address);
		String ip4Country = ip4Result.getCountryShort();
		if (ip4Country != null) {
			return ip4Country;
		}

		IPResult ip6Result = ip6.IPQuery(address);
		return ip6Result.getCountryShort();
	}
}