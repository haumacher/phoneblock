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
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.Message;

/**
 * Process of answering a single DNS query received via UDP.
 * 
 * <p>
 * The implementation of this class was inspired by <code>UDPSocketMonitor</code> from <em>EagleDNS</em> server.
 * </p>
 */
public class UdpRequest implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(UdpRequest.class);

	private final DnsServer _server;
	private final DatagramSocket _socket;
	private final DatagramPacket _rxPacket;

	public UdpRequest(DnsServer server, DatagramSocket socket, DatagramPacket rxPacket) {
		super();
		_server = server;
		_socket = socket;
		_rxPacket = rxPacket;
	}

	public void run() {
		try {
			Message query = new Message(_rxPacket.getData());
			
			Message response = _server.process(query);
			if (response == null) {
				return;
			}

			byte[] data = response.toWire();
			DatagramPacket txPacket = new DatagramPacket(data, data.length, _rxPacket.getAddress(), _rxPacket.getPort());

			try {
				_socket.send(txPacket);
			} catch (IOException ex) {
				LOG.warn("Error sending UDP response to " + _rxPacket.getAddress() + ", " + ex);
			}
		} catch (Throwable ex) {
			LOG.warn("Error processing UDP connection from " + _rxPacket.getSocketAddress() + ", " + ex, ex);
		}
	}
}
