package de.haumacher.phoneblock.mail.check;

import java.lang.reflect.Constructor;
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
import de.haumacher.phoneblock.mail.check.db.DBEmailCheck;
import de.haumacher.phoneblock.mail.check.db.Domains;
import de.haumacher.phoneblock.mail.check.model.DomainCheck;
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

			Object value = envCtx.lookup("mailcheck/providers");
			if (value != null) {
				for (String className : value.toString().split("\\s*,\\s*")) {
					if (className.isEmpty()) {
						continue;
					}
					loadProvider(envCtx, className);
				}
			}
		} catch (NamingException ex) {
			LOG.info("No mailcheck providers configured: " + ex.getMessage());
		}

		INSTANCE = this;
	}

	private void loadProvider(Context envCtx, String className) {
		try {
			Class<?> clazz = Class.forName(className);
			Constructor<?> ctor = clazz.getConstructor(Context.class);
			DomainCheckProvider provider = (DomainCheckProvider) ctor.newInstance(envCtx);
			_providers.add(provider);
			LOG.info("Loaded domain check provider: {}", className);
		} catch (Exception ex) {
			LOG.warn("Failed to load domain check provider '{}': {}", className, ex.getMessage());
		}
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

			// Step 1: Normalize and check EMAIL_CHECK cache
			String normalizedEmail = EmailNormalizer.normalize(address);
			if (normalizedEmail != null) {
				DBEmailCheck emailCheck = domains.checkEmailAddress(normalizedEmail);
				if (emailCheck != null) {
					return emailCheck.isDisposable();
				}

				for (DomainCheckProvider provider : _providers) {
					DomainCheck result = provider.checkNormalizedEmail(normalizedEmail);
					if (result != null) {
						persistEmailResult(tx, domains, normalizedEmail, result);
						return result.isDisposable();
					}
				}
			}

			// Step 2: Domain-level check
			DBDomainCheck check = domains.checkDomain(domainName);
			if (check != null) {
				return check.isDisposable();
			}

			for (DomainCheckProvider provider : _providers) {
				DomainCheck result = provider.checkEmail(address);
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

	private void persistEmailResult(SqlSession tx, Domains domains, String normalizedEmail, DomainCheck result) {
		try {
			domains.insertEmailCheck(normalizedEmail, result.isDisposable(), System.currentTimeMillis(), result.getSourceSystem());
			tx.commit();
		} catch (Exception ex) {
			LOG.error("Failed to persist e-mail check result for '{}'.", normalizedEmail, ex);
		}
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
