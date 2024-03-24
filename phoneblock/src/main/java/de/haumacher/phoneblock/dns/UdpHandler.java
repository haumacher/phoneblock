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
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.LoggerFactory;

/**
 * Process handling incoming UDP packets with DNS queries.
 * 
 * <p>
 * The implementation of this class was inspired by <code>UDPConnection</code> from <em>EagleDNS</em> server.
 * </p>
 */
public class UdpHandler implements Runnable {

	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(UdpHandler.class);

	private final DnsServer _server;
	private static final short UDP_LENGTH = 512;
	private final DatagramSocket _socket;

	private Executor _executor;

	public UdpHandler(Executor executor, DnsServer server, final InetAddress addr, final int port) throws SocketException {
		_executor = executor;
		_server = server;
		_socket = new DatagramSocket(port, addr);
	}

	@Override
	public void run() {
		LOG.info("Starting UDP handler.");

		while (_server.isActive()) {
			try {
				byte[] in = new byte[UDP_LENGTH];
				DatagramPacket indp = new DatagramPacket(in, in.length);
				indp.setLength(in.length);
				_socket.receive(indp);

				LOG.debug("UDP connection from " + indp.getSocketAddress());

				if (_server.isActive()) {
					_executor.execute(new UdpRequest(_server, _socket, indp));
				}
			} catch (RejectedExecutionException e) {
				if (_server.isActive()) {
					LOG.warn("Thread pool exausted, rejecting connection.");
				}
			} catch (Throwable ex) {
				if (_server.isActive()) {
					LOG.error("Error handling request.", ex);
				}
			}
		}

		LOG.info("UDP handler shutdown");
	}

	public void stop() throws IOException {
		LOG.info("Closing UDP handler.");
		_socket.close();
	}
}
