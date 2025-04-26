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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.DNAMERecord;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Header;
import org.xbill.DNS.MXRecord;
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
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;
import org.xbill.DNS.Zone;

import de.haumacher.phoneblock.db.settings.AnswerBotDynDns;

/**
 * Simplified DNS server handling only a single zone.
 * 
 * <p>
 * The implementation of this class was inspired by <code>EagleDNS</code> and <code>AuthoritativeResolver</code> from <em>EagleDNS</em> server.
 * </p>
 */
public class DnsServer implements Runnable {
	
	public static final String DOMAIN_NAME = "box.phoneblock.net";
	
	public static final int FLAG_DNSSECOK = 1;
	public static final int FLAG_SIGONLY = 2;

	private static Logger LOG = LoggerFactory.getLogger(DnsServer.class);

	private final ExecutorService _executor;
	private final ServerSocket _serverSocket;
	
	private Zone _onlyZone;
	private transient boolean _shouldStop;
	private UdpHandler _udpHandler;
	private Name _origin;
	private int _dclass = DClass.IN;
	private long _ttlMaster = 600;
	private long _ttlClient = 60;

	public DnsServer(ExecutorService executorService, int port) throws IOException {
		_executor = executorService;

		try (ServerSocket test = new ServerSocket()) {
			LOG.info("Supported socket options: " + test.supportedOptions().stream().map(o -> o.name()).collect(Collectors.joining(", ")));
		}
		
		InetAddress bindAddr = null;
		
		_serverSocket = new ServerSocket(port, 128, bindAddr);
		_udpHandler = new UdpHandler(_executor, this, bindAddr, port);
		
		_origin = Name.fromString(DOMAIN_NAME + ".");
		
		Name nshost = Name.fromString("ns.phoneblock.net.");
		
		// SOA original record from DNS Console:
		// 
		// ns1.your-server.de. postmaster.your-server.de. 2024102012 86400 10800 3600000 3600
		Name admin = Name.fromString("haui.haumacher.de.");
		long serial = 2024102012;
		long refresh = 86400;
		long retry = 10800;
		long expire = 3600000;
		long minimum = 300;
		
		Record[] records = {
			new NSRecord(_origin, _dclass, _ttlMaster, nshost),
			new SOARecord(_origin, _dclass, _ttlMaster, nshost, admin, serial, refresh, retry, expire, minimum),
			new ARecord(_origin, _dclass, _ttlMaster, InetAddress.getByName("128.140.84.131")),
			new AAAARecord(_origin, _dclass, _ttlMaster, InetAddress.getByName("2a01:4f8:c17:6624::1")),
			new TXTRecord(_origin, _dclass, _ttlMaster, Collections.singletonList("v=spf1 +a +mx ?all")),
			new MXRecord(_origin, _dclass, _ttlMaster, 10, Name.fromString("www508.your-server.de."))
		};
		_onlyZone = new Zone(_origin, records);
	}
	
	public void updateARecord(String name, InetAddress address) throws TextParseException, UnknownHostException {
		Name fullName = globalName(name);
		clearRecords(fullName, Type.A);
		addARecord(fullName, address);
	}

	public void addARecord(String name, InetAddress address) throws TextParseException, UnknownHostException {
		addARecord(globalName(name), address);
	}

	private void addARecord(Name globalName, InetAddress address) {
		_onlyZone.addRecord(new ARecord(globalName, _dclass, _ttlClient, address));
	}

	public void updateAAAARecord(String name, InetAddress address) throws TextParseException, UnknownHostException {
		Name fullName = globalName(name);
		clearRecords(fullName, Type.AAAA);
		addAAAARecord(fullName, address);
	}

	public void addAAAARecord(String name, InetAddress address) throws TextParseException, UnknownHostException {
		Name fullName = globalName(name);
		addAAAARecord(fullName, address);
	}

	private void addAAAARecord(Name fullName, InetAddress address) {
		_onlyZone.addRecord(new AAAARecord(fullName, _dclass, _ttlClient, address));
	}

	public void clearARecords(String name) throws TextParseException {
		clearRecords(globalName(name), Type.A);
	}
	
	public void clearAAAARecords(String name) throws TextParseException {
		clearRecords(globalName(name), Type.AAAA);
	}
	
	private void clearRecords(Name fullName, int type) {
		SetResponse records = _onlyZone.findRecords(fullName, type);
		if (records != null && records.answers() != null) {
			for (RRset set : records.answers()) {
				for (Record r : set.rrs()) {
					_onlyZone.removeRecord(r);
				}
			}
		}
	}

	private Name globalName(String name) throws TextParseException {
		return Name.fromString(name, _origin);
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

		LOG.info("DNS query {} type {}: {}", name, Type.string(type), Rcode.string(rcode));
		LOG.debug("DNS result:\n{}", response);
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
				if (!name.isWild()) {
					// Answer must use same case as query, therefore use name from query..
					r = r.withName(name);
				}
				response.addRecord(r, section);
			}
		}
		if ((flags & (FLAG_SIGONLY | FLAG_DNSSECOK)) != 0) {
			for  (Record r : rrset.sigs()) {
				if (!name.isWild()) {
					// Answer must use same case as query, therefore use name from query..
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

	public void load(AnswerBotDynDns update) throws TextParseException, UnknownHostException {
		if (update.getIpv4().isEmpty()) {
			clearARecords(update.getDyndnsUser());
		} else {
			updateARecord(update.getDyndnsUser(), InetAddress.getByName(update.getIpv4()));
		}
		if (update.getIpv6().isEmpty()) {
			clearAAAARecords(update.getDyndnsUser());
		} else {
			updateAAAARecord(update.getDyndnsUser(), InetAddress.getByName(update.getIpv6()));
		}
	}

}
