package de.haumacher.phoneblock.mail.check;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.OptionalLong;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpServletResponse;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.mail.check.db.DBDomainCheck;
import de.haumacher.phoneblock.mail.check.db.Domains;
import de.haumacher.phoneblock.mail.check.model.RapidAPIResult;

/**
 * Checker for disposable e-mail addresses.
 * 
 * @see "https://github.com/disposable-email-domains/disposable-email-domains"
 * @see "https://mailcheck.p.rapidapi.com/"
 */
public class EMailCheckService implements EMailChecker, ServletContextListener {
	
	private static final Logger LOG = LoggerFactory.getLogger(EMailCheckService.class);

	private static final EMailChecker NONE = new EMailChecker() {
		@Override
		public boolean isDisposable(InternetAddress contact) throws AddressException {
			return false;
		}
	};
	
	private static EMailChecker INSTANCE = NONE;
	
	private DBService _dbService;
	private String _apiKey;

	volatile private long _pauseUntil;
	
	public EMailCheckService(DBService db) {
		_dbService = db;
	}
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		try {
			InitialContext initCtx = new InitialContext();
			Context envCtx = (Context) initCtx.lookup("java:comp/env");
			
			Object value = envCtx.lookup("mailcheck/apiKey");
			if (value == null) {
				LOG.info("No API key found in JNDI configuration.");
			} else {
				_apiKey = value.toString();
				LOG.info("Using API key from JNDI configuration.");
			}
		} catch (NamingException ex) {
			LOG.info("Not using JNDI configuration: " + ex.getMessage());
		}
		
		INSTANCE = this;
	}
	
	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		LOG.info("Shutting down e-mail checker.");
		_apiKey = null;
		
		if (INSTANCE == this) {
			INSTANCE = NONE;
		}
	}

	@Override
	public boolean isDisposable(InternetAddress contact) throws AddressException {
		String address = contact.getAddress();
		int domainSep = address.indexOf('@');
		String domain = (domainSep >= 0) ? address.substring(domainSep + 1) : address;
		String domainName = domain.toLowerCase();
		
		try (SqlSession tx = _dbService.db().openSession()) {
			Domains domains = tx.getMapper(Domains.class);
			
			DBDomainCheck check = domains.checkDomain(domainName);
			if (check != null) {
				return check.isDisposable();
			}

			if (_apiKey != null) {
				RapidAPIResult result = callCheckService(domainName);
				if (result != null) {
					rememberResult(tx, domains, result);
					return result.isDisposable();
				}
			}
		} catch (Exception ex) {
			LOG.error("Failed to check e-mail domain '" + domainName + "'.", ex);
		}
		
		return false;
	}

	private RapidAPIResult callCheckService(String domainName) throws IOException, InterruptedException {
		long pauseUntil = _pauseUntil;
		if (pauseUntil > 0) {
			long now = System.currentTimeMillis();
			if (pauseUntil > now) {
				return null;
			} else {
				_pauseUntil = 0;
			}
		}
		
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("https://mailcheck.p.rapidapi.com/?domain=" + URLEncoder.encode(domainName, StandardCharsets.UTF_8)))
				.header("X-RapidAPI-Key", _apiKey)
				.header("X-RapidAPI-Host", "mailcheck.p.rapidapi.com")
				.method("GET", HttpRequest.BodyPublishers.noBody())
				.build();
		HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
		
		OptionalLong cntRemaining = response.headers().firstValueAsLong("x-ratelimit-requests-remaining");
		if (response.statusCode() != HttpServletResponse.SC_OK) {
			LOG.warn("Failed to check e-mail domain '" + domainName + "': " + response);
			
			// Check limits.
			// "x-ratelimit-requests-remaining": "995"
			// "x-ratelimit-requests-reset": "2494052"
			if (cntRemaining.isPresent()) {
				long cnt = cntRemaining.getAsLong();
				if (cnt == 0) {
					OptionalLong secondsDelay = response.headers().firstValueAsLong("x-ratelimit-requests-reset");
					if (secondsDelay.isPresent()) {
						long seconds = secondsDelay.getAsLong();
						_pauseUntil = System.currentTimeMillis() + seconds * 1000;
						
						LOG.warn("Quota exceeded, pausing for " + seconds + " seconds.");
					}
				}
			}
			
			return null;
		}
		
		RapidAPIResult result = RapidAPIResult.readRapidAPIResult(new JsonReader(new ReaderAdapter(new StringReader(response.body()))));
		
		LOG.info("Checked new e-mail domain '" + domainName + "' (quota left " + cntRemaining.orElse(-1) + "): " + (result.isDisposable() ? "DISPOSABLE": "OK"));
		
		return result;
	}

	private void rememberResult(SqlSession tx, Domains domains, RapidAPIResult result) {
		// 2024-02-07T17:09:29+01:00
		String lastChangedString = result.getLastChanged();
		
		long lastChangedMillis;
		DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
		try {
			Date lastChanged = fmt.parse(result.getLastChanged());
			lastChangedMillis = lastChanged.getTime();
		} catch (ParseException e) {
			LOG.error("Failed to parse lastChanged result: " + lastChangedString);
			lastChangedMillis = 0;
		}
		
		try {
			domains.insertDomain(result.getDomainName(), result.isDisposable(), lastChangedMillis, 1, result.getMxHost(), result.getMxIP());
			tx.commit();
		} catch (Exception ex) {
			LOG.error("Failed to remember e-mail domain check result: " + result, ex);
		}
	}

	public static EMailChecker getInstance() {
		return INSTANCE;
	}
}
