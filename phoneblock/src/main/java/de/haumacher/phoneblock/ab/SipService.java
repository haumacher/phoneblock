/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.ab;

import java.io.File;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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
		AnswerbotConfig botOptions = new AnswerbotConfig();
		
		loadConfig(sipConfig, portConfig, botOptions);
		sipConfig.normalize();
		resolveViaAddress(sipConfig);
		
		Scheduler scheduler = Scheduler.of(_scheduler.executor());
		
		_sipProvider = new SipProvider(sipConfig, scheduler);
		_portPool = portConfig.createPool();
		_answerBot = new AnswerBot(_sipProvider, botOptions, this::getCustomer, _portPool);
		
		_instance = this;
		
		_scheduler.executor().scheduleAtFixedRate(this::registerBots, 10, 5 * 60, TimeUnit.SECONDS);
	}

	private void resolveViaAddress(SipConfig sipConfig) {
		sipConfig.setViaAddrIPv4(resolve(AddressType.IP4, sipConfig.getViaAddrIPv4()));
		sipConfig.setViaAddrIPv6(resolve(AddressType.IP6, sipConfig.getViaAddrIPv6()));
	}

	private String resolve(AddressType type, String hostName) {
		boolean resolveV6 = type == AddressType.IP6;
		
		try {
			for (InetAddress address : InetAddress.getAllByName(hostName)) {
				boolean ipv6 = address instanceof Inet6Address;
				if (resolveV6 == ipv6) {
					return address.getHostAddress();
				}
			}
		} catch (UnknownHostException e) {
			// Ignore.
		}
		return hostName;
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
			String host = getHost(bot);
			if (host == null || host.isEmpty()) {
				failed.add(bot);
			} else {
				register(bot);
			}
		}

		if (!failed.isEmpty()) {
			try (SqlSession session = db.openSession()) {
				Users users = session.getMapper(Users.class);
			
				for (AnswerBotSip bot : failed) {
					LOG.warn("Disabling answer bot without host address: " + bot.getUserId() + "/" + bot.getUserName());
					users.enableAnswerBot(bot.getUserId(), false, _lastRegister);
				}

				session.commit();
			}
		}
	}

	private String getHost(AnswerBotSip bot) {
		String host = bot.getHost();
		if (host == null || host.isEmpty()) {
			host = bot.getIpv6();
			if (host == null || host.isEmpty()) {
				host = bot.getIpv4();
			}
		}
		return host;
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
	
	public void enableAnwserBot(String userName, boolean enable) {
		try (SqlSession tx = _dbService.db().openSession()) {
			Users users = tx.getMapper(Users.class);
			
			DBAnswerBotSip bot = users.getAnswerBot(userName);
			if (bot == null) {
				LOG.warn("User with ID '" + userName + "' not found.");
				return;
			}
			
			users.enableAnswerBot(bot.getUserId(), enable, System.currentTimeMillis());
			tx.commit();
			
			if (enable) {
				register(bot);
			} else {
				Registration registration = _clients.remove(bot.getUserName());
				if (registration == null) {
					LOG.info("No active registration for user '" + userName + "'.");
					return;
				}

				LOG.info("Stopping answer bot '" + userName + "'.");
				registration.halt();
			}
		}
	}

	/** 
	 * Dynamically registers a new answer bot.
	 */
	public void register(AnswerBotSip bot) {
		register(bot.getUserId(), toCustomerConfig(bot));
	}

	private CustomerConfig toCustomerConfig(AnswerBotSip bot) {
		CustomerConfig regConfig = new CustomerConfig();
		regConfig.setUser(bot.getUserName());
		regConfig.setPasswd(bot.getPasswd());
		regConfig.setRealm(bot.getRealm());
		regConfig.setRegistrar(new SipURI(bot.getRegistrar()));
		regConfig.setRoute(new SipURI(getHost(bot)).addLr());
		return regConfig;
	}

	private void register(long userId, CustomerOptions customerConfig) {
		try {
			Registration client = new Registration(_sipProvider, userId, customerConfig, this);
			Registration clash = _clients.put(customerConfig.getUser(), client);
			
			if (clash != null) {
				clash.halt();
			}
			
			LOG.info("Started registration for " + customerConfig.getUser() + ".");
			client.loopRegister(customerConfig);
		} catch (Exception ex) {
			LOG.error("Registration for " + customerConfig.getUser() + " failed.", ex);
		}
	}

	@Override
	public void onRegistrationSuccess(RegistrationClient registration, NameAddress target, NameAddress contact, int expires,
			int renewTime, String result) {
		long userId = ((Registration) registration).getUserId();
		updateRegistration(userId, true, result);
		LOG.info("Sucessfully registered " + registration.getUsername() + ": " + result);
	}

	@Override
	public void onRegistrationFailure(RegistrationClient registration, NameAddress target, NameAddress contact,
			String result) {
		long userId = ((Registration) registration).getUserId();
		updateRegistration(userId, false, result);
		LOG.warn("Failed to register '" + registration.getUsername() + "': " + result);
	}

	private void updateRegistration(long userId, boolean registered, String result) {
		try (SqlSession session = _dbService.db().openSession()) {
			Users users = session.getMapper(Users.class);
			
			int cnt = users.updateSipRegistration(userId, registered, result);
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
