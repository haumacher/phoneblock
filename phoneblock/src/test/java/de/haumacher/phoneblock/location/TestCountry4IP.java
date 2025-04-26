package de.haumacher.phoneblock.location;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.InetAddress;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.location.Country4IP;

/**
 * Test case for {@link Country4IP}.
 */
public class TestCountry4IP {
	
	@Test
	public void testLookup() throws IOException {
		Country4IP country = new Country4IP("./target/IP2LOCATION/IP2LOCATION-LITE-DB1.BIN", "./target/IP2LOCATION/IP2LOCATION-LITE-DB1.IPV6.BIN");
		
        assertEquals("DE", country.lookup("phoneblock.net"));
        assertEquals("DE", country.lookup("128.140.84.131"));
        assertEquals("DE", country.lookup("2a01:4f8:c17:6624::1"));
        
        assertEquals("DE", country.lookup(InetAddress.getByName("128.140.84.131")));
        assertEquals("DE", country.lookup(InetAddress.getByName("2a01:4f8:c17:6624::1")));
	}
	
}
