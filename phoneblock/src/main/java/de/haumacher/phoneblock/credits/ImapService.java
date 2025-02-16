package de.haumacher.phoneblock.credits;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.session.SqlSession;
import org.eclipse.angus.mail.imap.IdleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;
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
			File propsFile = new File(".mail.properties");
			if (propsFile.exists()) {
				try (InputStream in = new FileInputStream(propsFile)) {
					_properties.load(in);
				}
			}
			openSession();
		} catch (IOException | MessagingException ex) {
			LOG.error("Failed to initialize IMAP service.", ex);
		}
		
		_scheduler.scheduler().scheduleAtFixedRate(this::checkSession, 1, 1, TimeUnit.HOURS);
	}

	private void openSession() throws NoSuchProviderException, MessagingException, IOException {
		_session = Session.getInstance(_properties);
		_store = _session.getStore("imaps");
		String user = _properties.getProperty("mail.imap.user");
		String password = _properties.getProperty("mail.imap.password");
		_store.connect(user, password);
		
		IdleManager observer = new IdleManager(_session, _scheduler);
		
		_inbox = _store.getFolder("INBOX");
		_inbox.open(Folder.READ_ONLY);
		
		searchNewMessages();
		
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
		            observer.watch(_inbox); 
		        } catch (MessagingException mex) {
		            // handle exception related to the Folder
		        }
		    }
		});
		observer.watch(_inbox);
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
			
			String value = users.getProperty("imap.lastSearch");
			long lastSearch;
			if (value == null) {
				lastSearch = 0;
			} else {
				lastSearch = Long.parseLong(value);
			}
			
			SearchTerm query = messagePattern();
			if (lastSearch > 0) {
				query =new AndTerm(query, 
					new ReceivedDateTerm(DateTerm.GT, new Date(lastSearch)));
			}
			
			Message[] newMessages = _inbox.search(query);
			if (newMessages.length > 0) {
				processMessages(users, Arrays.asList(newMessages));
				tx.commit();
			}
		}
	}

	private AndTerm messagePattern() throws AddressException {
		return new AndTerm(
			new FromTerm(new InternetAddress("service@paypal.de")),
			new SubjectTerm("Du hast eine Zahlung erhalten")
		);
	}

	private void setLastSearch(Users users, long lastSearch) {
		int ok = users.updateProperty("imap.lastSearch", Long.toString(lastSearch));
		if (ok == 0) {
			users.addProperty("imap.lastSearch", Long.toString(lastSearch));
		}
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
    			messageDetails.dump();
        		
        		DB.processContribution(users, messageDetails);
			} catch (Exception ex) {
				System.err.println("Failed to process message: " + ex.getMessage());
			}
        }
		
		if (latest > 0) {
			setLastSearch(users, latest);
		}
	}

	private void checkSession() {
		try {
			searchNewMessages();
		} catch (Exception ex) {
			LOG.error("Failed to access inbox.", ex);
			closeConnection();
			try {
				openSession();
			} catch (MessagingException | IOException e1) {
				LOG.error("Failed to re-open IMAP session.", e1);
			}
		}
	}

}
