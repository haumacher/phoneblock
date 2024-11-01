package de.haumacher.phoneblock.dns;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Starts a DNS server for testing.
 */
public class TestDnsServer {

	public static void main(String[] args) throws IOException {
		ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);
		DnsServer server = new DnsServer(executor, 5300);
		
		server.addARecord("test", (Inet4Address) InetAddress.getByAddress(new byte[] {1, 2, 3, 4}));
		server.addAAAARecord("test", (Inet6Address) InetAddress.getByAddress(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}));
		
		server.start();

		System.out.println("Press RETURN to stop service.");
		new BufferedReader(new InputStreamReader(System.in)).readLine();
		
		server.stop();
		executor.shutdown();
	}

}
