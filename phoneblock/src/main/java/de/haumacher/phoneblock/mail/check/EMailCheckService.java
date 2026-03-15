package de.haumacher.phoneblock.mail.check;

import java.util.ArrayList;
import java.util.List;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.mail.check.db.DBDomainCheck;
import de.haumacher.phoneblock.mail.check.db.Domains;
import de.haumacher.phoneblock.mail.check.model.DomainCheck;
import de.haumacher.phoneblock.mail.check.provider.rapidapi.RapidAPIProvider;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Orchestrator for disposable e-mail domain checks.
 *
 * <p>
 * Delegates to a chain of {@link DomainCheckProvider}s after checking the DB cache.
 * The first provider returning a non-{@code null} result wins.
 * </p>
 *
 * @see "https://github.com/disposable-email-domains/disposable-email-domains"
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
	private final List<DomainCheckProvider> _providers = new ArrayList<>();

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
				_providers.add(new RapidAPIProvider(value.toString()));
				LOG.info("Using RapidAPI provider for e-mail domain checks.");
			}
		} catch (NamingException ex) {
			LOG.info("Not using JNDI configuration: " + ex.getMessage());
		}

		INSTANCE = this;
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		LOG.info("Shutting down e-mail checker.");
		_providers.clear();

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

			for (DomainCheckProvider provider : _providers) {
				DomainCheck result = provider.checkDomain(domainName);
				if (result != null) {
					persistResult(tx, domains, result);
					return result.isDisposable();
				}
			}
		} catch (Exception ex) {
			LOG.error("Failed to check e-mail domain '" + domainName + "'.", ex);
		}

		return false;
	}

	private void persistResult(SqlSession tx, Domains domains, DomainCheck result) {
		try {
			domains.insertDomain(result.getDomainName(), result.isDisposable(), result.getLastChanged(),
					result.getSourceSystem(), result.getMxHost(), result.getMxIP());
			tx.commit();
		} catch (Exception ex) {
			LOG.error("Failed to persist e-mail domain check result: " + result, ex);
		}
	}

	public static EMailChecker getInstance() {
		return INSTANCE;
	}
}
