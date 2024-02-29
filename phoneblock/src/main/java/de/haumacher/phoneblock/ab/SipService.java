/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.ab;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.ibatis.session.SqlSession;
import org.kohsuke.args4j.ClassParser;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.mjsip.pool.PortConfig;
import org.mjsip.pool.PortPool;
import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.address.SipURI;
import org.mjsip.sip.provider.SipConfig;
import org.mjsip.sip.provider.SipProvider;
import org.mjsip.time.Scheduler;
import org.mjsip.ua.registration.RegistrationClient;
import org.mjsip.ua.registration.RegistrationClientListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;
import org.zoolu.net.AddressType;
import org.zoolu.util.ConfigFile;

import de.haumacher.phoneblock.answerbot.AnswerBot;
import de.haumacher.phoneblock.answerbot.AnswerbotConfig;
import de.haumacher.phoneblock.answerbot.CustomerConfig;
import de.haumacher.phoneblock.answerbot.CustomerOptions;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBAnswerBotSip;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.db.settings.AnswerBotSip;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Service managing a SIP stack.
 */
public class SipService implements ServletContextListener, RegistrationClientListener {
	
	private static final Pattern IPV4_PATTERN = Pattern.compile("[1-9][0-9]*\\.[1-9][0-9]*\\.[1-9][0-9]*\\.[1-9][0-9]*");

	private static final Logger LOG = LoggerFactory.getLogger(SipService.class);

	private SchedulerService _scheduler;
	private DBService _dbService;
	
	private SipProvider _sipProvider;
	private PortPool _portPool;
	private AnswerBot _answerBot;
	
	private final ConcurrentHashMap<String, Registration> _clients = new ConcurrentHashMap<>();

	private long _lastRegister;
	
	private static SipService _instance;

	private String _fileName;

	private Collection<String> _jndiOptions;

	private SipServiceConfig _config;

	/**
	 * The initially configured VIA IPv4 address.
	 */
	private String _viaV4;

	/**
	 * The initially configured VIA IPv6 address.
	 */
	private String _viaV6;

	/** 
	 * Creates a {@link SipService}.
	 */
	public SipService(SchedulerService scheduler, DBService dbService) {
		_scheduler = scheduler;
		_dbService = dbService;
	}
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		loadJndiOptions();
		
		start();
	}
	
	private void loadJndiOptions() {
		_fileName = null;
		_jndiOptions = new ArrayList<>();
		try {
			InitialContext initCtx = new InitialContext();
			Context envCtx = (Context) initCtx.lookup("java:comp/env");

			NamingEnumeration<NameClassPair> options = envCtx.list("answerbot");
			for (; options.hasMore(); ) {
				NameClassPair pair = options.next();
				String name = pair.getName();
				Object lookup = envCtx.lookup("answerbot/" + name);
				
				if ("configfile".equals(name)) {
					_fileName = lookup.toString();
				} else {
					_jndiOptions.add(name);
					if (lookup != null) {
						_jndiOptions.add(lookup.toString());
					}
				}
			}
		} catch (NamingException ex) {
			LOG.error("Error loading JDNI configuration: " + ex.getMessage());
		}
	}
	
	public void start() {
		if (_answerBot != null) {
			LOG.warn("SIP service already active.");
			return;
		}
		LOG.info("Starting SIP service.");
		
		SipConfig sipConfig = new SipConfig();
		PortConfig portConfig = new PortConfig();
		portConfig.setMediaPort(50061);
		portConfig.setPortCount(20);
		_config = new SipServiceConfig();
		AnswerbotConfig botOptions = new AnswerbotConfig();
		
		loadConfig(sipConfig, portConfig, botOptions);
		sipConfig.normalize();
		
		_viaV4 = sipConfig.getViaAddrIPv4();
		_viaV6 = sipConfig.getViaAddrIPv6();
		
		LOG.info("Configured IPv4 via address: " + _viaV4);
		LOG.info("Configured IPv6 via address: " + _viaV6);
		
		resolveViaAddress(sipConfig);
		
		Scheduler scheduler = Scheduler.of(_scheduler.executor());
		
		_sipProvider = new SipProvider(sipConfig, scheduler);
		_portPool = portConfig.createPool();
		_answerBot = new AnswerBot(_sipProvider, botOptions, this::getCustomer, _portPool) {
			@Override
			protected void processCallData(String userName, String from, long startTime, long duration) {
				super.processCallData(userName, from, startTime, duration);
				
				DB db = _dbService.db();
				try (SqlSession session = db.openSession()) {
					Users users = session.getMapper(Users.class);
					
					long id = users.getAnswerBotId(userName);
					users.recordCall(id, from, startTime, duration);
					users.recordCallSummary(id, duration);
					
					session.commit();
				}
			}
		};
		
		_instance = this;
		
		_scheduler.executor().scheduleAtFixedRate(this::registerBots, 10, 5 * 60, TimeUnit.SECONDS);
	}

	private void resolveViaAddress(SipConfig sipConfig) {
		if (_viaV4 != null) {
			String ipv4 = resolve(AddressType.IP4, _viaV4);
			if (ipv4 == null) {
				sipConfig.setViaAddrIPv4(null);
				LOG.warn("No IPv4 via address found for: " + _viaV4);
			} else {
				if (!ipv4.equals(sipConfig.getViaAddrIPv4())) {
					sipConfig.setViaAddrIPv4(ipv4);
					LOG.info("Updated IPv4 via address to: " + ipv4);
				}
			}
		}
		
		if (_viaV6 != null) {
			String ipv6 = resolve(AddressType.IP6, _viaV6);
			if (ipv6 == null) {
				sipConfig.setViaAddrIPv4(null);
				LOG.warn("No IPv6 via address found for: " + _viaV6);
			} else {
				if (!ipv6.equals(sipConfig.getViaAddrIPv6())) {
					sipConfig.setViaAddrIPv6(ipv6);
					LOG.info("Updated IPv6 via address to: " + ipv6);
				}
			}
		}
	}

	/**
	 * Looks up the given host name from DNS.
	 * 
	 * <p>
	 * Note: The default host name resolution from the Java virtual machine is not
	 * appropriate, because its cache does not respect TTL timeouts and may even
	 * cache infinitely. It is only configurable globally.
	 * </p>
	 */
	public static String resolve(AddressType type, String hostName) {
		if (hostName.indexOf(':') >= 0) {
			// A IPv6 address.
			return hostName;
		}
		if (IPV4_PATTERN.matcher(hostName).matches()) {
			// A IPv4 address.
			return hostName;
		}
		
		Name name;
		try {
			name = Name.fromString(hostName);
		} catch (TextParseException e) {
			LOG.error("Invalid host name: " + hostName);
			return null;
		}

		switch (type) {
		case DEFAULT:
		case IP6: {
			Record[] result = new Lookup(name, Type.AAAA, DClass.IN).run();
			if (result == null) {
				return null;
			}
			
			return ((AAAARecord) result[0]).getAddress().getHostAddress();
		}
		case IP4:
			Record[] result = new Lookup(name, Type.A, DClass.IN).run();
			if (result == null) {
				return null;
			}
			
			return ((ARecord) result[0]).getAddress().getHostAddress();
		}
		
		throw new IllegalArgumentException("No such address type: " + type);
	}
	
	/**
	 * Registers all answer bots that have been updated since the last run.
	 */
	private void registerBots() {
		resolveViaAddress((SipConfig) _sipProvider.sipConfig());
		
		long since = _lastRegister;
		_lastRegister = System.currentTimeMillis();
		
		DB db = _dbService.db();
		
		List<? extends AnswerBotSip> bots;
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			
			bots = users.getEnabledAnswerBots(since);
		}
		
		List<AnswerBotSip> failed = new ArrayList<>();
		for (AnswerBotSip bot : bots) {
			try {
				register(bot, false);
			} catch (UnknownHostException e) {
				failed.add(bot);
			}
		}

		if (!failed.isEmpty()) {
			try (SqlSession session = db.openSession()) {
				Users users = session.getMapper(Users.class);
			
				for (AnswerBotSip bot : failed) {
					LOG.warn("Disabling answer bot without host address: " + bot.getUserId() + "/" + bot.getUserName());
					users.enableAnswerBot(bot.getId(), false, _lastRegister);
				}

				session.commit();
			}
		}
	}

	private String getHost(AnswerBotSip bot) throws UnknownHostException {
		String host = bot.getHost();
		if (host == null || host.isEmpty()) {
			String ipv6 = bot.getIpv6();
			if (ipv6 != null && !ipv6.isEmpty()) {
				return ipv6;
			}
			String ipv4 = bot.getIpv4();
			if (ipv4 != null && !ipv4.isEmpty()) {
				return ipv4;
			}
			throw new UnknownHostException("Neither host name nor DynDNS configured for: " + bot.getUserName());
		} else {
			String ipv6 = resolve(AddressType.IP6, host);
			if (ipv6 != null) {
				return ipv6;
			}
			String ipv4 = resolve(AddressType.IP4, host);
			if (ipv4 != null) {
				return ipv4;
			}
			throw new UnknownHostException("Cannot resolve host name '" + host + "' for: " + bot.getUserName());
		}
	}
	
	private void loadConfig(Object...beans) {
		CmdLineParser parser = new CmdLineParser(null);
		for (Object bean : beans) {
			new ClassParser().parse(bean, parser);
		}
		
		try {
			Collection<String> fileOptions;
			if (_fileName != null) {
				File file = new File(_fileName);
				if (!file.exists()) {
					LOG.error("Answerbot configuration file does not exits: " + file.getAbsolutePath());
				}
				
				LOG.info("Loading configuration from: " + _fileName);
				ConfigFile configFile = new ConfigFile(file);
				fileOptions = configFile.toArguments();
			} else {
				fileOptions = Collections.emptyList();
			}
			
			Collection<String> arguments = new ArrayList<>(fileOptions);
			arguments.addAll(_jndiOptions);
			
			parser.parseArgument(arguments);
		} catch (CmdLineException ex) {
			LOG.error("Invalid answer bot configuration: " + ex.getMessage());
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		stop();
	}
	
	public void stop() {
		LOG.info("Stopping SIP service.");
		for (RegistrationClient client : _clients.values()) {
			client.halt();
		}
		_clients.clear();

		if (_answerBot != null) {
			_answerBot.halt();
		}
		_answerBot = null;
		_portPool = null;
		
		if (_sipProvider != null) {
			_sipProvider.halt();
			_sipProvider = null;
		}
		
		if (_instance == this) {
			_instance = null;
		}
	}
	
	/**
	 * The {@link SipService} singleton.
	 */
	public static SipService getInstance() {
		return _instance;
	}
	
	public void enableAnwserBot(String userName) throws UnknownHostException {
		enableAnwserBot(userName, false);
	}
	
	public void enableAnwserBot(String userName, boolean temporary) throws UnknownHostException {
		try (SqlSession tx = _dbService.db().openSession()) {
			Users users = tx.getMapper(Users.class);
			
			DBAnswerBotSip bot = users.getAnswerBotBySipUser(userName);
			if (bot == null) {
				LOG.warn("User with ID '" + userName + "' not found.");
				return;
			}
			
			register(bot, temporary);
		}
	}

	public void disableAnwserBot(String userName) {
		try (SqlSession tx = _dbService.db().openSession()) {
			Users users = tx.getMapper(Users.class);
			
			DBAnswerBotSip bot = users.getAnswerBotBySipUser(userName);
			if (bot == null) {
				LOG.warn("User with ID '" + userName + "' not found.");
				return;
			}
			
			users.enableAnswerBot(bot.getId(), false, System.currentTimeMillis());
			tx.commit();
			
			stop(userName);
		}
	}

	private void stop(String userName) {
		Registration registration = _clients.remove(userName);
		if (registration == null) {
			LOG.info("No active registration for user '" + userName + "'.");
			return;
		}

		LOG.info("Stopping answer bot '" + userName + "'.");
		registration.halt();
	}

	/**
	 * Dynamically registers a new answer bot.
	 * 
	 * @param temporary Whether the bot is first activated and should only be
	 *                  permanently activated, if the first registration succeeds.
	 */
	public void register(AnswerBotSip bot, boolean temporary) throws UnknownHostException {
		register(bot, toCustomerConfig(bot), temporary);
	}

	private CustomerConfig toCustomerConfig(AnswerBotSip bot) throws UnknownHostException {
		CustomerConfig regConfig = new CustomerConfig();
		regConfig.setUser(bot.getUserName());
		regConfig.setPasswd(bot.getPasswd());
		regConfig.setRealm(bot.getRealm());
		regConfig.setRegistrar(new SipURI(bot.getRegistrar()));
		regConfig.setRoute(new SipURI(getHost(bot)).addLr());
		return regConfig;
	}

	private void register(AnswerBotSip bot, CustomerOptions customerConfig, boolean temporary) {
		try {
			Registration client = new Registration(_sipProvider, bot, customerConfig, this, temporary);
			Registration clash = _clients.put(bot.getUserName(), client);
			
			if (clash != null) {
				clash.halt();
			}
			
			LOG.info("Started registration for " + customerConfig.getUser() + ".");
			client.loopRegister(customerConfig);
		} catch (Exception ex) {
			LOG.error("Registration for " + customerConfig.getUser() + " failed.", ex);
		}
	}
	
	/**
	 * Whether a registration client is active for the given user name.
	 */
	public boolean isActive(String userName) {
		return _clients.get(userName) != null;
	}

	@Override
	public void onRegistrationSuccess(RegistrationClient client, NameAddress target, NameAddress contact, int expires,
			int renewTime, String result) {
		Registration registration = (Registration) client;
		long id = registration.getId();
		
		if (registration.isTemporary()) {
			// Bot was only started temporarily, now enable permanently.
			
			try (SqlSession session = _dbService.db().openSession()) {
				Users users = session.getMapper(Users.class);
				
				users.enableAnswerBot(id, true, System.currentTimeMillis());
				session.commit();
			}
			
			registration.setPermanent();
		}
		registration.resetFailures();

		updateRegistration(id, true, result);

		LOG.info("Sucessfully registered " + client.getUsername() + ": " + result);
	}

	@Override
	public void onRegistrationFailure(RegistrationClient client, NameAddress target, NameAddress contact,
			String result) {
		Registration registration = (Registration) client;
		long id = registration.getId();

		registration.incFailures();
		int failures = registration.getFailures();

		LOG.warn("Failed to register '" + client.getUsername() + "' (" + failures + " failures): " + result);
		updateRegistration(id, false, result);
		
		boolean temporary = registration.isTemporary();
		if (temporary || failures > _config.maxFailures) {
			LOG.warn("Stopping " + (temporary ? "temporary " : "") + "registration '" + client.getUsername() + "'.");
			stop(registration.getUsername());
		} else {
			AnswerBotSip bot = registration.getBot();
			try {
				// Try to update IP address.
				registration.setCustomer(toCustomerConfig(bot));
			} catch (UnknownHostException ex) {
				LOG.warn("Stopping registration due to failed hostname resolution: " + client.getUsername());
				stop(registration.getUsername());
			}
		}
	}

	private void updateRegistration(long id, boolean registered, String result) {
		try (SqlSession session = _dbService.db().openSession()) {
			Users users = session.getMapper(Users.class);
			
			int cnt = users.updateSipRegistration(id, registered, result);
			if (cnt > 0) {
				session.commit();
			}
		}
	}

	private CustomerOptions getCustomer(String userName) {
		Registration registration = _clients.get(userName);
		if (registration == null) {
			return null;
		}
		return registration.getCustomer();
	}

}
