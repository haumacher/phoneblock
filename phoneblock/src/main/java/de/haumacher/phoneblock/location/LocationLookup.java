package de.haumacher.phoneblock.location;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import de.haumacher.phoneblock.app.render.DefaultController;
import de.haumacher.phoneblock.location.model.Country;
import de.haumacher.phoneblock.shared.Language;
import jakarta.servlet.http.HttpServletRequest;

public interface LocationLookup {

	LocationLookup NONE = addr -> null;

	Country getCountry(InetAddress address);

	default Country getCountry(String address) {
		try {
			return getCountry(InetAddress.getByName(address));
		} catch (UnknownHostException e) {
			return null;
		}
	}

	default Country getCountry(HttpServletRequest request) {
		String ipAddress = request.getHeader("X-Forwarded-For");  
		if (ipAddress == null) {  
		    ipAddress = request.getRemoteAddr();  
		} else {
			int separatorIndex = ipAddress.indexOf(',');
			if (separatorIndex >= 0) {
				// Use first address, since this is the client address, others are proxy addresses.
				ipAddress = ipAddress.substring(0, separatorIndex).trim();
			}
		}
		
		return getCountry(ipAddress);
	}
	
	default String browserDialPrefix(HttpServletRequest req) {
		Country country = LocationService.getInstance().getCountry(req);
		if (country != null) {
			List<String> dialPrefixes = country.getDialPrefixes();
			if (!dialPrefixes.isEmpty()) {
				return dialPrefixes.get(0);
			}
		}
		
		Language lang = DefaultController.selectLanguage(req);
		
		return lang.dialPrefix;
	}
	
}
