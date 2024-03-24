/*******************************************************************************
 * Copyright (c) 2010 Robert "Unlogic" Olofsson (unlogic@unlogic.se).
 * Copyright (c) 2024 Bernhard Haumacher (haui@haumacher.de).
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v3
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-3.0-standalone.html
 ******************************************************************************/
package de.haumacher.phoneblock.dns;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.DNAMERecord;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Header;
import org.xbill.DNS.Message;
import org.xbill.DNS.NSRecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.NameTooLongException;
import org.xbill.DNS.Opcode;
import org.xbill.DNS.RRset;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.SOARecord;
import org.xbill.DNS.Section;
import org.xbill.DNS.SetResponse;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;
import org.xbill.DNS.Zone;

/**
 * Simplified DNS server handling only a single zone.
 * 
 * <p>
 * The implementation of this class was inspired by <code>EagleDNS</code> and <code>AuthoritativeResolver</code> from <em>EagleDNS</em> server.
 * </p>
 */
public class DnsServer implements Runnable {
	
	public static final int FLAG_DNSSECOK = 1;
	public static final int FLAG_SIGONLY = 2;

	private static Logger LOG = LoggerFactory.getLogger(DnsServer.class);

	private final ScheduledExecutorService _executor;
	private final ServerSocket _serverSocket;
	
	private Zone _onlyZone;
	private transient boolean _shouldStop;
	private UdpHandler _udpHandler;
	private Name _origin;
	private int dclass;
	private long ttl;

	public DnsServer(ScheduledExecutorService executor, int port) throws IOException {
		_executor = executor;
		_serverSocket = new ServerSocket(port, 128);
		_udpHandler = new UdpHandler(_executor, this, null, port);
		
		_origin = Name.fromString("box.phoneblock.net.");
		
		dclass = DClass.IN;
		ttl = 600;
		Name nshost = Name.fromString("phoneblock.net.");
		Name admin = Name.fromString("play.haumacher.de.");
		long serial = 0;
		long refresh = 86400;
		long retry = 7200;
		long expire = 3600000;
		long minimum = 300;
		
		Record[] records = {
			new SOARecord(_origin, dclass, ttl, nshost, admin, serial, refresh, retry, expire, minimum),
			new NSRecord(_origin, dclass, ttl, nshost),
		};
		_onlyZone = new Zone(_origin, records);
	}
	
	public void addARecord(String name, Inet4Address address) throws TextParseException, UnknownHostException {
		_onlyZone.addRecord(new ARecord(Name.fromString(name, _origin), dclass, ttl, address));
	}
	
	public void addAAAARecord(String name, Inet6Address address) throws TextParseException, UnknownHostException {
		_onlyZone.addRecord(new AAAARecord(Name.fromString(name, _origin), dclass, ttl, address));
	}

	public DnsServer start() {
		LOG.info("DNS server started.");
		
		_executor.execute(this);
		_executor.execute(_udpHandler);
		
		return this;
	}

	@Override
	public void run() {
		while (!_shouldStop) {
			try {
				Socket socket = _serverSocket.accept();
				_executor.execute(new TcpHandler(this, socket));
			} catch (Throwable ex) {
				if (!_shouldStop) {
					LOG.error("Accept failed.", ex);
				}
			}
		}
		LOG.info("DNS server loop terminated.");
	}

	public void stop() {
		try {
			_shouldStop = true;
			_serverSocket.close();
			_udpHandler.stop();
			LOG.info("DNS server stopped.");
		} catch (IOException ex) {
			LOG.warn("Stop failed.", ex);
		}
	}

	public Message process(Message query) {
		Record queryRecord = query.getQuestion();
		
		if(queryRecord == null){
			LOG.warn("No query given.");
			return null;
		}
		
		Name name = queryRecord.getName();
		
		Zone zone = findBestZone(name);
		if(zone == null){
			LOG.warn("No zone found for: " + name);
			return null;
		}
		
		// boolean badversion;
		int flags = 0;

		Header header = query.getHeader();
		if (header.getFlag(Flags.QR)) {
			LOG.warn("QR flag found: " + name);
			return null;
		}
		if (header.getRcode() != Rcode.NOERROR) {
			LOG.warn("Rejecting query with error: " + name);
			return null;
		}
		if (header.getOpcode() != Opcode.QUERY) {
			LOG.warn("No not a query: " + name);
			return null;
		}
		
		Message response = new Message(query.getHeader().getID());
		response.getHeader().setFlag(Flags.QR);
		if (query.getHeader().getFlag(Flags.RD)) {
			response.getHeader().setFlag(Flags.RD);
		}

		response.addRecord(queryRecord, Section.QUESTION);

		int type = queryRecord.getType();
		int dclass = queryRecord.getDClass();
		if (type == Type.AXFR) {
			LOG.warn("Rejected zone transfer: " + name);
			return null;
		}
		if (!Type.isRR(type) && type != Type.ANY) {
			LOG.warn("Query for '" + name + "' with invalid type rejected: " + Type.string(type));
			return null;
		}

		byte rcode = addAnswer(response, name, type, dclass, 0, flags,zone);

		if (rcode != Rcode.NOERROR && rcode != Rcode.NXDOMAIN) {
			LOG.warn("Query for '" + name + "' failed: " + Rcode.string(rcode));
			return errorMessage(query, rcode);
		}

		LOG.info("DNS query for '" + name + "': " + Rcode.string(rcode));

		return response;
	}
	
	private byte addAnswer(Message response, Name name, int type, int dclass, int iterations, int flags, Zone zone) {
		byte rcode = Rcode.NOERROR;

		if (iterations > 6) {
			return Rcode.NOERROR;
		}

		if (type == Type.SIG || type == Type.RRSIG) {
			type = Type.ANY;
			flags |= FLAG_SIGONLY;
		}
		
		if (zone != null) {
			SetResponse sr = zone.findRecords(name, type);
			if (sr.isNXDOMAIN()) {
				response.getHeader().setRcode(Rcode.NXDOMAIN);
				if (zone != null) {
					addSOA(response, zone);
					if (iterations == 0) {
						response.getHeader().setFlag(Flags.AA);
					}
				}
				rcode = Rcode.NXDOMAIN;
			} else if (sr.isNXRRSET()) {
				if (zone != null) {
					addSOA(response, zone);
					if (iterations == 0) {
						response.getHeader().setFlag(Flags.AA);
					}
				}
			} else if (sr.isDelegation()) {
				RRset nsRecords = sr.getNS();
				addRRset(nsRecords.getName(), response, nsRecords, Section.AUTHORITY, flags);
			} else if (sr.isCNAME()) {
				CNAMERecord cname = sr.getCNAME();
				RRset rrset = new RRset(cname);
				addRRset(name, response, rrset, Section.ANSWER, flags);
				if (zone != null && iterations == 0) {
					response.getHeader().setFlag(Flags.AA);
				}
				rcode = addAnswer(response, cname.getTarget(), type, dclass, iterations + 1, flags,null);
			} else if (sr.isDNAME()) {
				DNAMERecord dname = sr.getDNAME();
				RRset rrset = new RRset(dname);
				addRRset(name, response, rrset, Section.ANSWER, flags);
				Name newname;
				try {
					newname = name.fromDNAME(dname);
				} catch (NameTooLongException e) {
					return Rcode.YXDOMAIN;
				}
				rrset = new RRset(new CNAMERecord(name, dclass, 0, newname));
				addRRset(name, response, rrset, Section.ANSWER, flags);
				if (zone != null && iterations == 0) {
					response.getHeader().setFlag(Flags.AA);
				}
				rcode = addAnswer(response, newname, type, dclass, iterations + 1, flags,null);
			} else if (sr.isSuccessful()) {
				for (RRset rrset : sr.answers()) {
					addRRset(name, response, rrset, Section.ANSWER, flags);
				}
				if (zone != null) {
					addNS(response, zone, flags);
					if (iterations == 0) {
						response.getHeader().setFlag(Flags.AA);
					}
				}
			}
		}

		return rcode;
	}
	
	private void addRRset(Name name, Message response, RRset rrset, int section, int flags) {
		for (int s = 1; s <= section; s++) {
			if (response.findRRset(name, rrset.getType(), s)) {
				return;
			}
		}
		if ((flags & FLAG_SIGONLY) == 0) {
			for (Record r : rrset.rrs()) {
				if (r.getName().isWild() && !name.isWild()) {
					r = r.withName(name);
				}
				response.addRecord(r, section);
			}
		}
		if ((flags & (FLAG_SIGONLY | FLAG_DNSSECOK)) != 0) {
			for  (Record r : rrset.sigs()) {
				if (r.getName().isWild() && !name.isWild()) {
					r = r.withName(name);
				}
				response.addRecord(r, section);
			}
		}
	}

	private final void addNS(Message response, Zone zone, int flags) {
		RRset nsRecords = zone.getNS();
		addRRset(nsRecords.getName(), response, nsRecords, Section.AUTHORITY, flags);
	}

	private final void addSOA(Message response, Zone zone) {
		response.addRecord(zone.getSOA(), Section.AUTHORITY);
	}

	private Zone findBestZone(Name name) {
		Name origin = _onlyZone.getOrigin();
		if (name.equals(origin)) {
			return _onlyZone;
		}

		int labels = name.labels();
		for (int i = 1; i < labels; i++) {
			Name tname = new Name(name, i);

			if (tname.equals(origin)) {
				return _onlyZone;
			}
		}

		return null;
	}
	
	public static Message errorMessage(Message query, int rcode) {
		return buildErrorMessage(query.getHeader(), rcode, query.getQuestion());
	}

	public static Message buildErrorMessage(Header header, int rcode, Record question) {
		Message response = new Message();

		response.setHeader(header);
		for (int i = 0; i < 4; i++) {
			response.removeAllRecords(i);
		}
		if (rcode == Rcode.SERVFAIL) {
			response.addRecord(question, Section.QUESTION);
		}
		header.setRcode(rcode);
		return response;
	}

	public boolean isActive() {
		return !_shouldStop;
	}

}
