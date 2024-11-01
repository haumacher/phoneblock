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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.Message;

/**
 * Handler for incoming DNS requests via TCP.
 * 
 * <p>
 * The implementation of this class was inspired by <code>TCPConnection</code> from <em>EagleDNS</em> server.
 * </p>
 */
public class TcpHandler implements Runnable {

	private static Logger LOG = LoggerFactory.getLogger(TcpHandler.class);

	private Socket _socket;

	private DnsServer _server;

	public TcpHandler(DnsServer server, Socket socket) {
		_server = server;
		_socket = socket;
	}

	@Override
	public void run() {
		try (InputStream in = _socket.getInputStream()) {
			DataInputStream dataIn = new DataInputStream(in);
			int querySize = dataIn.readUnsignedShort();
			byte[] request = new byte[querySize];
			dataIn.readFully(request);

			Message query = new Message(request);
			LOG.info("DNS query " + query.getQuestion() + " from TCP " + _socket.getRemoteSocketAddress());

			Message response = _server.process(query);
			if (response != null) {
				DataOutputStream dataOut = new DataOutputStream(_socket.getOutputStream());
				byte[] packet = response.toWire(65535);
				dataOut.writeShort(packet.length);
				dataOut.write(packet);
			}
		} catch (IOException ex) {
			LOG.error("Failed to process query from " + _socket.getRemoteSocketAddress() + ": " + description(ex));
		}
	}

	private String description(IOException ex) {
		String message = ex.getMessage();
		String type = ex.getClass().getSimpleName();
		return message == null ? ex.getClass().getSimpleName() : message + " (" + type + ")";
	}

}
