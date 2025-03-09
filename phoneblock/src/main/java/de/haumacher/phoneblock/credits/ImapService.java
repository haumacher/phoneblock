package de.haumacher.phoneblock.credits;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.naming.NamingException;

import org.apache.ibatis.session.SqlSession;
import org.eclipse.angus.mail.imap.IdleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.jndi.JNDIProperties;
import de.haumacher.phoneblock.mail.MailService;
import de.haumacher.phoneblock.mail.MailServiceStarter;
import de.haumacher.phoneblock.scheduler.SchedulerService;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.NoSuchProviderException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.DateTerm;
import jakarta.mail.search.FromTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SubjectTerm;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

public class ImapService implements ServletContextListener {
	private static final Logger LOG = LoggerFactory.getLogger(ImapService.class);
	
	private final MailParser _parser = new MailParser();
	private final SchedulerService _scheduler;
	private final DBService _dbService;
	private final MailServiceStarter _mail;
	
	private Properties _properties = new Properties();
	
	private Session _session;
	
	private Store _store;
	private Folder _inbox;

	private boolean _sendThanks;

	private boolean _active;


	public ImapService(SchedulerService scheduler, DBService dbService, MailServiceStarter mail) {
		_scheduler = scheduler;
		_dbService = dbService;
		_mail = mail;
	}
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		LOG.info("Starting IMAP service.");
		try {
			JNDIProperties jndi = new JNDIProperties();
			_properties = jndi.lookupProperties("imap");
			
			_active = "true".equals(jndi.lookupString("credits.active"));
			_sendThanks = "true".equals(jndi.lookupString("credits.sendmails"));
			
			if (_active) {
				try {
					openSession();
				} catch (MessagingException | IOException ex) {
					LOG.error("Failed to initialize IMAP service.", ex);
				}
				
				if (_session != null) {
					_scheduler.scheduler().scheduleAtFixedRate(this::checkSession, 10, 60*60, TimeUnit.SECONDS);
				}
			} else {
				LOG.info("Donation processing is deactivated.");
			}
		} catch (NamingException ex) {
			LOG.error("Failed to configure IMAP service.", ex);
		}
	}

	private void openSession() throws NoSuchProviderException, MessagingException, IOException {
		String user = _properties.getProperty("mail.imap.user");
		String password = _properties.getProperty("mail.imap.password");
		
		if (user == null || password == null) {
			LOG.warn("Do not start IMAP service, no credentials provided.");
			return;
		}
		
		_session = Session.getInstance(_properties);
		_store = _session.getStore("imaps");
		_store.connect(user, password);
		
		_inbox = _store.getFolder("INBOX");
		_inbox.open(Folder.READ_ONLY);
		
// Watching inbox seems not to work
		
//		_inbox.addMessageCountListener(new MessageCountAdapter() {
//		    public void messagesAdded(MessageCountEvent e) {
//		        List<Message> messages = Arrays.asList(e.getMessages());
//		        try {
//		    		processMessages(messages);
//
//		        	// Keep watching for new messages.
//		    		startWatcher(observer);
//		        } catch (MessagingException mex) {
//		            // handle exception related to the Folder
//		        }
//		    }
//		});
//
//		IdleManager observer = new IdleManager(_session, _scheduler);
//		startWatcher(observer);
	}

	private void startWatcher(IdleManager observer) {
		try {
			observer.watch(_inbox);
		} catch (MessagingException ex) {
			LOG.error("Failed to start inbox watcher.", ex);
		}
	}
	
	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		LOG.info("Shutting down IMAP service.");
		closeConnection();
	}

	private void closeConnection() {
		if (_inbox != null) {
			try {
				_inbox.close(false);
			} catch (MessagingException ex) {
				LOG.error("Failed to close inbox.", ex);
			}
		}
		if (_store != null) {
			try {
				_store.close();
			} catch (MessagingException ex) {
				LOG.error("Failed to close IMAP store.", ex);
			}
		}
	}
	
	private void processMessages(List<Message> allMessages) throws AddressException {
		SearchTerm pattern = messagePattern();
		
		List<Message> matchingMessages = allMessages.stream().filter(pattern::match).toList();
		LOG.info("Received {} messages ({} donations).", allMessages.size(), matchingMessages.size());

		try (SqlSession tx = _dbService.db().openSession()) {
			Users users = tx.getMapper(Users.class);

			processMessages(tx, users, matchingMessages);
    		updateTimestamp(users, allMessages);
			
			tx.commit();
		}
	}

	private AndTerm messagePattern() throws AddressException {
		return new AndTerm(
			new FromTerm(new InternetAddress("service@paypal.de")),
			new SubjectTerm("Du hast eine Zahlung erhalten")
		);
	}

	private void processMessages(SqlSession tx, Users users, List<Message> newMessages) {
		for (Message message : newMessages) {
        	try {
        		MessageDetails messageDetails = _parser.parse(message);

				UserSettings userSettings = DB.processContribution(users, messageDetails);
        		
				if (_sendThanks && userSettings != null) {
					MailService mailService = _mail.getMailService();
					if (mailService != null) {
						boolean ok = mailService.sendThanksMail(messageDetails.sender, userSettings, messageDetails.amount);
						if (ok) {
							users.ackContribution(messageDetails.tx);
						}
					}
				}
				
				tx.commit();
			} catch (Exception ex) {
				System.err.println("Failed to process message: " + ex.getMessage());
			}
        }
	}

	private void updateTimestamp(Users users, List<Message> allMessages) {
		long latest = 0;
		for (Message message : allMessages) {
			long received = received(message);
			latest = Math.max(latest, received);
		}
		if (latest > 0) {
			DB.setLastSearch(users, latest);
		}
	}

	private static long received(Message message) {
		try {
			Date receivedDate = message.getReceivedDate();
			return receivedDate == null ? 0 : receivedDate.getTime();
		} catch (MessagingException ex) {
			LOG.error("Failed to access message.", ex);
			return 0;
		}
	}

	private void checkSession() {
		try {
			LOG.info("Checking for new mails.");
			searchNewMessages();
		} catch (Exception ex) {
			LOG.error("Failed to access inbox.", ex);
			closeConnection();
			try {
				openSession();
				
				// Try again.
				searchNewMessages();
			} catch (MessagingException | IOException e1) {
				LOG.error("Failed to re-open IMAP session.", e1);
			}
		}
	}

	void searchNewMessages() throws MessagingException {
		try (SqlSession tx = _dbService.db().openSession()) {
			Users users = tx.getMapper(Users.class);
			
			long lastSearch = DB.getLastSearch(users);
			
			SearchTerm query = messagePattern();
			if (lastSearch > 0) {
				// Note: IMAP date query has granularity day. Therefore, when using GT
				// comparison, messages are skipped if received the same day after a search.
				// When using GE comparison, messages are repeatedly found in each search and
				// must be skipped manually.
				query = new AndTerm(query, 
					new ReceivedDateTerm(DateTerm.GE, new Date(lastSearch)));
			}
			
			Message[] result = _inbox.search(query);
			List<Message> newMessages = Arrays.stream(result)
					.filter(m -> received(m) > lastSearch)
					.sorted(Comparator.comparingLong(ImapService::received))
					.toList();
			
			if (newMessages.size() > 0) {
				processMessages(tx, users, newMessages);
	    		updateTimestamp(users, newMessages);
				
				tx.commit();
			} else {
				LOG.info("No new mails found.");
			}
		}
	}

}
