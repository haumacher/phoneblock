package de.haumacher.phoneblock.dns;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.InetAddress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.NSRecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.RRset;
import org.xbill.DNS.Record;
import org.xbill.DNS.SOARecord;
import org.xbill.DNS.SetResponse;
import org.xbill.DNS.Type;
import org.xbill.DNS.Zone;

/**
 * Test to check the functionality of the {@link Zone} class.
 */
public class TestZone {

	private Name origin;
	private long ttl;
	private int dclass;
	private Zone zone;
	
	@BeforeEach
	public void setup() throws IOException {
		origin = Name.fromString("foo.bar.");
		dclass = DClass.IN;
		ttl = 600;
		long serial = 0;
		long refresh = 600;
		long retry = 600;
		long expire = 600;
		long minimum = 600;
		zone = new Zone(origin, new Record[] {
			new SOARecord(origin, dclass, ttl, Name.fromString("ns", origin), Name.fromString("info", origin), serial, refresh, retry, expire, minimum),
			new NSRecord(origin, dclass, ttl, Name.fromString("ns", origin))
		});
	}

	@Test
	public void testZoneAdd() throws IOException {
		Name test = Name.fromString("test1", origin);
		zone.addRecord(new ARecord(test, dclass, ttl, InetAddress.getByAddress(new byte[] {1, 2, 3, 4})));
		zone.addRecord(new ARecord(test, dclass, ttl, InetAddress.getByAddress(new byte[] {1, 2, 3, 5})));
		SetResponse records1 = zone.findRecords(test, Type.A);
		assertEquals(1, records1.answers().size());
		assertEquals(2, records1.answers().get(0).size());
	}
	
	@Test
	public void testZoneReplace() throws IOException {
		Name test = Name.fromString("test2", origin);
		zone.addRecord(new ARecord(test, dclass, ttl, InetAddress.getByAddress(new byte[] {1, 2, 3, 4})));
		for (RRset set : zone.findRecords(test, Type.A).answers()) {
			for (Record r : set.rrs()) {
				zone.removeRecord(r);
			}
		}
		zone.addRecord(new ARecord(test, dclass, ttl, InetAddress.getByAddress(new byte[] {1, 2, 3, 5})));
		SetResponse records2 = zone.findRecords(test, Type.A);
		assertEquals(1, records2.answers().size());
		assertEquals(1, records2.answers().get(0).size());
	}
	
}
