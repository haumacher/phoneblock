/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.net.MalformedURLException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import de.haumacher.phoneblock.crawl.WebCrawler;

/**
 * TODO
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
@WebListener
public class Application implements ServletContextListener {

	private Thread _crawlerThread;

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		try {
			_crawlerThread = new Thread(new WebCrawler());
			_crawlerThread.start();
		} catch (MalformedURLException ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		if (_crawlerThread != null) {
			_crawlerThread.interrupt();
			_crawlerThread = null;
		}
	}

}
