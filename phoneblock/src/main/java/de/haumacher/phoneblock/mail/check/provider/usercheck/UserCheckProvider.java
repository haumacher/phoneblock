package de.haumacher.phoneblock.mail.check.provider.usercheck;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import javax.naming.Context;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.phoneblock.mail.check.DomainCheckProvider;
import de.haumacher.phoneblock.mail.check.model.DomainCheck;
import de.haumacher.phoneblock.mail.check.provider.usercheck.model.UserCheckResult;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@link DomainCheckProvider} that queries the UserCheck API.
 *
 * <p>
 * Requires the JNDI property {@code usercheck/apiKey} to be set.
 * </p>
 *
 * @see <a href="https://app.usercheck.com/docs#check-domain">UserCheck API Documentation</a>
 */
public class UserCheckProvider implements DomainCheckProvider {

	/** The provider identifier stored in the {@code SOURCE_SYSTEM} column. */
	public static final String PROVIDER_ID = "usercheck";

	private static final Logger LOG = LoggerFactory.getLogger(UserCheckProvider.class);

	private static final long PAUSE_DURATION_MS = 60_000;

	private final String _apiKey;

	volatile private long _pauseUntil;

	/**
	 * Creates a {@link UserCheckProvider} reading its configuration from JNDI.
	 *
	 * @param envCtx The JNDI environment context ({@code java:comp/env}).
	 * @throws NamingException If the required {@code usercheck/apiKey} property is not configured.
	 */
	public UserCheckProvider(Context envCtx) throws NamingException {
		Object value = envCtx.lookup("usercheck/apiKey");
		if (value == null) {
			throw new NamingException("No 'usercheck/apiKey' configured for UserCheck provider.");
		}
		_apiKey = value.toString();
	}

	/**
	 * Creates a {@link UserCheckProvider} with an explicit API key (e.g. for testing).
	 */
	public UserCheckProvider(String apiKey) {
		_apiKey = apiKey;
	}

	@Override
	public String getProviderId() {
		return PROVIDER_ID;
	}

	@Override
	public DomainCheck checkEmail(String email) {
		String domainName = extractDomain(email);
		try {
			UserCheckResult result = callCheckService(domainName);
			if (result == null) {
				return null;
			}
			return toDomainCheck(result);
		} catch (Exception ex) {
			LOG.error("Failed to check e-mail domain '{}' via UserCheck.", domainName, ex);
			return null;
		}
	}

	UserCheckResult callCheckService(String domainName) throws IOException, InterruptedException {
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
				.uri(URI.create("https://api.usercheck.com/domain/" + URLEncoder.encode(domainName, StandardCharsets.UTF_8)))
				.header("Authorization", "Bearer " + _apiKey)
				.method("GET", HttpRequest.BodyPublishers.noBody())
				.build();
		HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

		int statusCode = response.statusCode();

		if (statusCode == 429) {
			_pauseUntil = System.currentTimeMillis() + PAUSE_DURATION_MS;
			LOG.warn("UserCheck rate limit exceeded, pausing for {} seconds.", PAUSE_DURATION_MS / 1000);
			return null;
		}

		if (statusCode == 400) {
			LOG.info("UserCheck returned 400 for domain '{}', skipping.", domainName);
			return null;
		}

		if (statusCode != HttpServletResponse.SC_OK) {
			LOG.warn("Failed to check e-mail domain '{}' via UserCheck: HTTP {}", domainName, statusCode);
			return null;
		}

		UserCheckResult result = UserCheckResult.readUserCheckResult(new JsonReader(new ReaderAdapter(new StringReader(response.body()))));

		LOG.info("Checked e-mail domain '{}' via UserCheck: {}", domainName,
				result.isDisposable() ? "DISPOSABLE" : "OK");

		return result;
	}

	private static String extractDomain(String email) {
		int domainSep = email.indexOf('@');
		String domain = (domainSep >= 0) ? email.substring(domainSep + 1) : email;
		return domain.toLowerCase();
	}

	/**
	 * Converts a {@link UserCheckResult} to a {@link DomainCheck}.
	 */
	static DomainCheck toDomainCheck(UserCheckResult result) {
		return DomainCheck.create()
				.setDomainName(result.getDomainName())
				.setDisposable(result.isDisposable())
				.setLastChanged(System.currentTimeMillis())
				.setSourceSystem(PROVIDER_ID);
	}
}
