package de.haumacher.phoneblock.credits;

import java.io.IOException;
import java.util.Arrays;
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
import de.haumacher.phoneblock.jndi.JNDIProperties;
import de.haumacher.phoneblock.scheduler.SchedulerService;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.NoSuchProviderException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.event.MessageCountAdapter;
import jakarta.mail.event.MessageCountEvent;
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
	
	private Properties _properties = new Properties();
	
	private Session _session;
	
	private Store _store;
	private Folder _inbox;
	
	public ImapService(SchedulerService scheduler, DBService dbService) {
		_scheduler = scheduler;
		_dbService = dbService;
	}
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		try {
			JNDIProperties jndi = new JNDIProperties();
			_properties = jndi.lookupProperties("imap");
			
			try {
				openSession();
			} catch (MessagingException | IOException ex) {
				LOG.error("Failed to initialize IMAP service.", ex);
			}
			
			if (_session != null) {
				_scheduler.scheduler().scheduleAtFixedRate(this::checkSession, 10, 60*60, TimeUnit.SECONDS);
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
		
		IdleManager observer = new IdleManager(_session, _scheduler);
		
		_inbox = _store.getFolder("INBOX");
		_inbox.open(Folder.READ_ONLY);
		
		_inbox.addMessageCountListener(new MessageCountAdapter() {
		    public void messagesAdded(MessageCountEvent e) {
		        Message[] messages = e.getMessages();
		        try {
		    		SearchTerm pattern = messagePattern();
		    		
		    		List<Message> matchingMessages = Arrays.stream(messages).filter(pattern::match).toList();
		    		if (!matchingMessages.isEmpty()) {
		    			// Process messages.
		    			processMessages(matchingMessages);
		    		}
		        	
		        	// Keep watching for new messages.
		    		startWatcher(observer);
		        } catch (MessagingException mex) {
		            // handle exception related to the Folder
		        }
		    }
		});

		startWatcher(observer);
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
	
	void searchNewMessages() throws MessagingException {
		try (SqlSession tx = _dbService.db().openSession()) {
			Users users = tx.getMapper(Users.class);
			
			long lastSearch = DB.getLastSearch(users);
			
			SearchTerm query = messagePattern();
			if (lastSearch > 0) {
				query =new AndTerm(query, 
					new ReceivedDateTerm(DateTerm.GT, new Date(lastSearch)));
			}
			
			Message[] newMessages = _inbox.search(query);
			if (newMessages.length > 0) {
				processMessages(users, Arrays.asList(newMessages));
				tx.commit();
			} else {
				LOG.info("No new mails found.");
			}
		}
	}

	private AndTerm messagePattern() throws AddressException {
		return new AndTerm(
			new FromTerm(new InternetAddress("service@paypal.de")),
			new SubjectTerm("Du hast eine Zahlung erhalten")
		);
	}

	private void processMessages(List<Message> newMessages) throws AddressException {
		try (SqlSession tx = _dbService.db().openSession()) {
			Users users = tx.getMapper(Users.class);

			processMessages(users, newMessages);
			
			tx.commit();
		}
	}
	
	private void processMessages(Users users, List<Message> newMessages) {
		long latest = 0;
		for (Message message : newMessages) {
        	try {
        		long received = message.getReceivedDate().getTime();
        		latest = Math.max(latest, received);

        		MessageDetails messageDetails = _parser.parse(message);

				LOG.info("Processing donation from {}/{} ({} Ct).", messageDetails.sender, messageDetails.uid, messageDetails.amount);
        		
        		DB.processContribution(users, messageDetails);
			} catch (Exception ex) {
				System.err.println("Failed to process message: " + ex.getMessage());
			}
        }
		
		if (latest > 0) {
			DB.setLastSearch(users, latest);
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

}
