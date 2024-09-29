/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.index;

import jakarta.servlet.ServletContextEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link IndexUpdateService} forwarding calls to multiple {@link IndexUpdateService} instances.
 */
final class IndexUpdateMultiplexer implements IndexUpdateService {
	private static final Logger LOG = LoggerFactory.getLogger(IndexUpdateMultiplexer.class);

	private final IndexUpdateService[] _services;

	/** 
	 * Creates a {@link IndexUpdateMultiplexer}.
	 *
	 * @param services
	 */
	IndexUpdateMultiplexer(IndexUpdateService[] services) {
		_services = services;
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		for (IndexUpdateService service : _services) {
			try {
				service.contextInitialized(sce);
			} catch (Exception ex) {
				LOG.error("Starting service failed: " + service, ex);
			}
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		for (IndexUpdateService service : _services) {
			try {
				service.contextDestroyed(sce);
			} catch (Exception ex) {
				LOG.error("Stopping service failed: " + service, ex);
			}
		}
	}

	@Override
	public void publishPathUpdate(String path) {
		for (IndexUpdateService service : _services) {
			try {
				service.publishPathUpdate(path);
			} catch (Exception ex) {
				LOG.error("Sending update '" + path + "' failed: " + service, ex);
			}
		}
	}
}