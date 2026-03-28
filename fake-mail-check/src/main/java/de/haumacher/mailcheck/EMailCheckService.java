package de.haumacher.mailcheck;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.mailcheck.db.DBDomainCheck;
import de.haumacher.mailcheck.db.DBEmailCheck;
import de.haumacher.mailcheck.db.DBMxStatus;
import de.haumacher.mailcheck.db.Domains;
import de.haumacher.mailcheck.db.MailCheckSchema;
import de.haumacher.mailcheck.dns.MxLookup;
import de.haumacher.mailcheck.dns.MxResult;
import de.haumacher.mailcheck.model.DomainCheck;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Orchestrator for disposable e-mail domain checks.
 *
 * <p>
 * Delegates to a chain of {@link EMailCheckProvider}s after checking the DB cache.
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

	private final SqlSessionFactory _sessionFactory;
	private final List<EMailCheckProvider> _providers = new ArrayList<>();

	public EMailCheckService(SqlSessionFactory sessionFactory) {
		_sessionFactory = sessionFactory;
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		MailCheckSchema.initialize(_sessionFactory);

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
			EMailCheckProvider provider = (EMailCheckProvider) ctor.newInstance(envCtx);
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

		try (SqlSession tx = _sessionFactory.openSession()) {
			Domains domains = tx.getMapper(Domains.class);

			// Step 1: Normalize and check EMAIL_CHECK cache
			String normalizedEmail = EmailNormalizer.normalize(address);
			if (normalizedEmail != null) {
				DBEmailCheck emailCheck = domains.checkEmailAddress(normalizedEmail);
				if (emailCheck != null) {
					return emailCheck.isDisposable();
				}

				for (EMailCheckProvider provider : _providers) {
					DomainCheck result = provider.checkEmail(address);
					if (result != null) {
						persistEmailResult(tx, domains, normalizedEmail, result);
						return result.isDisposable();
					}
				}
				
				// Public E-Mail provider, but not a known disposable address.
				return false;
			}

			// Step 2: Domain-level check
			DBDomainCheck check = domains.checkDomain(domainName);
			if (check != null) {
				return check.isDisposable();
			}

			// Step 3: MX-based heuristic
			MxResult mx = MxLookup.lookup(domainName);
			Boolean mxVerdict = checkMxStatus(domains, mx);
			if (mxVerdict != null) {
				long now = System.currentTimeMillis();
				domains.insertDomain(domainName, mxVerdict, now, "mx-lookup", mx.mxHost(), mx.mxIp());
				tx.commit();
				LOG.info("MX-based classification for '{}': {} (MX: {})", domainName, mxVerdict ? "disposable" : "safe", mx.mxHost());
				return mxVerdict;
			}

			// Step 4: Ask external providers
			for (EMailCheckProvider provider : _providers) {
				DomainCheck result = provider.checkDomain(domainName);
				if (result != null) {
					// Enrich with MX data if provider didn't supply it.
					if (result.getMxHost() == null && mx.mxHost() != null) {
						result.setMxHost(mx.mxHost());
					}
					if (result.getMxIP() == null && mx.mxIp() != null) {
						result.setMxIP(mx.mxIp());
					}
					persistResult(tx, domains, result);
					updateMxStatus(tx, domains, mx, result.isDisposable());
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

	/**
	 * Checks MX_HOST_STATUS and MX_IP_STATUS tables for a verdict.
	 *
	 * @return {@code true} if disposable, {@code false} if safe, {@code null} if unknown or mixed.
	 */
	private Boolean checkMxStatus(Domains domains, MxResult mx) {
		if (mx.mxHost() != null) {
			DBMxStatus hostStatus = domains.checkMxHost(mx.mxHost());
			if (hostStatus != null) {
				if (hostStatus.isDisposable()) return Boolean.TRUE;
				if (hostStatus.isSafe()) return Boolean.FALSE;
				// mixed → fall through
			}
		}

		if (mx.mxIp() != null) {
			DBMxStatus ipStatus = domains.checkMxIp(mx.mxIp());
			if (ipStatus != null) {
				if (ipStatus.isDisposable()) return Boolean.TRUE;
				if (ipStatus.isSafe()) return Boolean.FALSE;
			}
		}

		return null;
	}

	/**
	 * Updates MX_HOST_STATUS and MX_IP_STATUS after a provider response.
	 */
	private void updateMxStatus(SqlSession tx, Domains domains, MxResult mx, boolean disposable) {
		long now = System.currentTimeMillis();
		try {
			if (mx.mxHost() != null) {
				DBMxStatus existing = domains.checkMxHost(mx.mxHost());
				if (existing == null) {
					domains.insertMxHost(mx.mxHost(), disposable ? DBMxStatus.DISPOSABLE : DBMxStatus.SAFE, now);
				} else {
					String merged = DBMxStatus.mergeStatus(existing.getStatus(), disposable);
					if (!merged.equals(existing.getStatus())) {
						domains.updateMxHostStatus(mx.mxHost(), merged, now);
					}
				}
			}

			if (mx.mxIp() != null) {
				DBMxStatus existing = domains.checkMxIp(mx.mxIp());
				if (existing == null) {
					domains.insertMxIp(mx.mxIp(), disposable ? DBMxStatus.DISPOSABLE : DBMxStatus.SAFE, now);
				} else {
					String merged = DBMxStatus.mergeStatus(existing.getStatus(), disposable);
					if (!merged.equals(existing.getStatus())) {
						domains.updateMxIpStatus(mx.mxIp(), merged, now);
					}
				}
			}

			tx.commit();
		} catch (Exception ex) {
			LOG.error("Failed to update MX status for host={}, ip={}.", mx.mxHost(), mx.mxIp(), ex);
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
