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
import de.haumacher.mailcheck.db.DomainInsert;
import de.haumacher.mailcheck.db.Domains;
import de.haumacher.mailcheck.db.MailCheckSchema;
import de.haumacher.mailcheck.model.DomainStatus;
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
		public DomainStatus check(InternetAddress contact) throws AddressException {
			return DomainStatus.SAFE;
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
	public DomainStatus check(InternetAddress contact) throws AddressException {
		String address = contact.getAddress();
		if (address == null || address.indexOf('@') < 0) {
			return DomainStatus.INVALID;
		}

		String domainName = address.substring(address.indexOf('@') + 1).toLowerCase();

		try (SqlSession tx = _sessionFactory.openSession()) {
			Domains domains = tx.getMapper(Domains.class);

			// Step 1: Normalize and check EMAIL_CHECK cache
			String normalizedEmail = EmailNormalizer.toCanonicalPublicAddress(address);
			if (normalizedEmail != null) {
				DBEmailCheck emailCheck = domains.checkEmailAddress(normalizedEmail);
				if (emailCheck != null) {
					return emailCheck.isDisposable() ? DomainStatus.DISPOSABLE : DomainStatus.SAFE;
				}

				for (EMailCheckProvider provider : _providers) {
					DomainCheck result = provider.checkEmail(address);
					if (result != null) {
						persistEmailResult(tx, domains, normalizedEmail, result);
						return result.getStatus();
					}
				}

				// Public E-Mail provider, but not a known disposable address.
				return DomainStatus.SAFE;
			}

			// Step 2: Domain-level check
			DBDomainCheck check = domains.checkDomain(domainName);
			if (check != null) {
				return check.getStatus();
			}

			// Step 3: MX lookup
			MxResult mx = MxLookup.lookup(domainName);

			// No MX record → domain cannot receive mail → invalid.
			if (mx.mxHost() == null) {
				long now = System.currentTimeMillis();
				domains.insertDomain(domainName, DomainStatus.INVALID.protocolName(), now, "mx-lookup", "-", null);
				tx.commit();
				LOG.info("No MX record for '{}' — classified as invalid.", domainName);
				return DomainStatus.INVALID;
			}

			// Step 4: MX-based heuristic
			DomainStatus mxVerdict = checkMxStatus(domains, mx);
			if (mxVerdict != null) {
				long now = System.currentTimeMillis();
				domains.insertDomain(domainName, mxVerdict.protocolName(), now, "mx-lookup", mx.mxHost(), mx.mxIp());
				tx.commit();
				LOG.info("MX-based classification for '{}': {} (MX: {})", domainName, mxVerdict, mx.mxHost());
				return mxVerdict;
			}

			// Step 5: Ask external providers
			for (EMailCheckProvider provider : _providers) {
				DomainCheck result = provider.checkDomain(domainName);
				if (result != null) {
					// Enrich with MX data if provider didn't supply it.
					if (result.getMxHost() == null) {
						result.setMxHost(mx.mxHost());
					}
					if (result.getMxIP() == null && mx.mxIp() != null) {
						result.setMxIP(mx.mxIp());
					}
					persistResult(tx, domains, result);
					updateMxStatus(tx, domains, mx, result.getStatus() == DomainStatus.DISPOSABLE);
					return result.getStatus();
				}
			}
		} catch (Exception ex) {
			LOG.error("Failed to check e-mail domain '" + domainName + "'.", ex);
		}

		return DomainStatus.SAFE;
	}

	private void persistEmailResult(SqlSession tx, Domains domains, String normalizedEmail, DomainCheck result) {
		try {
			domains.insertEmailCheck(normalizedEmail, result.getStatus() == DomainStatus.DISPOSABLE, System.currentTimeMillis(), result.getSourceSystem());
			tx.commit();
		} catch (Exception ex) {
			LOG.error("Failed to persist e-mail check result for '{}'.", normalizedEmail, ex);
		}
	}

	/**
	 * Checks MX_HOST_STATUS and MX_IP_STATUS tables for a verdict.
	 *
	 * <p>
	 * Only trusts the classification if it is backed by at least
	 * {@link DBMxStatus#MIN_DOMAIN_COUNT} domains to avoid false
	 * classifications from small sample sizes. Only returns
	 * {@link DomainStatus#DISPOSABLE} as a shortcut — {@code safe}
	 * MX status is not trusted since there are far more legitimate
	 * domains than disposable ones, so the external provider should
	 * always verify.
	 * </p>
	 *
	 * @return {@link DomainStatus#DISPOSABLE} or {@code null} if not conclusively disposable.
	 */
	private DomainStatus checkMxStatus(Domains domains, MxResult mx) {
		if (mx.mxHost() != null) {
			DBMxStatus hostStatus = domains.checkMxHost(mx.mxHost());
			if (hostStatus != null && hostStatus.isTrusted() && hostStatus.isDisposable()) {
				return DomainStatus.DISPOSABLE;
			}
		}

		if (mx.mxIp() != null) {
			DBMxStatus ipStatus = domains.checkMxIp(mx.mxIp());
			if (ipStatus != null && ipStatus.isTrusted() && ipStatus.isDisposable()) {
				return DomainStatus.DISPOSABLE;
			}
		}

		return null;
	}

	/**
	 * Updates MX_HOST_STATUS and MX_IP_STATUS after a provider response.
	 */
	private void updateMxStatus(SqlSession tx, Domains domains, MxResult mx, boolean disposable) {
		try {
			DomainInsert.updateMxStatus(domains, mx, disposable, System.currentTimeMillis());
			tx.commit();
		} catch (Exception ex) {
			LOG.error("Failed to update MX status for host={}, ip={}.", mx.mxHost(), mx.mxIp(), ex);
		}
	}

	private void persistResult(SqlSession tx, Domains domains, DomainCheck result) {
		try {
			domains.insertDomain(result.getDomainName(), result.getStatus().protocolName(), result.getLastChanged(),
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
