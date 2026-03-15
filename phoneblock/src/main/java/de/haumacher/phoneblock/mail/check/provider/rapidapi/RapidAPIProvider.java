package de.haumacher.phoneblock.mail.check.provider.rapidapi;

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

import javax.naming.Context;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.phoneblock.mail.check.DomainCheckProvider;
import de.haumacher.phoneblock.mail.check.model.DomainCheck;
import de.haumacher.phoneblock.mail.check.provider.rapidapi.model.RapidAPIResult;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@link DomainCheckProvider} that queries the RapidAPI mailcheck service.
 *
 * <p>
 * Requires the JNDI property {@code mailcheck/apiKey} to be set.
 * </p>
 *
 * @see <a href="https://mailcheck.p.rapidapi.com/">RapidAPI Mailcheck</a>
 */
public class RapidAPIProvider implements DomainCheckProvider {

	private static final Logger LOG = LoggerFactory.getLogger(RapidAPIProvider.class);

	private final String _apiKey;

	volatile private long _pauseUntil;

	/**
	 * Creates a {@link RapidAPIProvider} reading its configuration from JNDI.
	 *
	 * @param envCtx The JNDI environment context ({@code java:comp/env}).
	 * @throws NamingException If the required {@code mailcheck/apiKey} property is not configured.
	 */
	public RapidAPIProvider(Context envCtx) throws NamingException {
		Object value = envCtx.lookup("mailcheck/apiKey");
		if (value == null) {
			throw new NamingException("No 'mailcheck/apiKey' configured for RapidAPI provider.");
		}
		_apiKey = value.toString();
	}

	/**
	 * Creates a {@link RapidAPIProvider} with an explicit API key (e.g. for testing).
	 */
	public RapidAPIProvider(String apiKey) {
		_apiKey = apiKey;
	}

	@Override
	public DomainCheck checkDomain(String domainName) {
		try {
			RapidAPIResult result = callCheckService(domainName);
			if (result == null) {
				return null;
			}
			return toDomainCheck(result);
		} catch (Exception ex) {
			LOG.error("Failed to check e-mail domain '{}' via RapidAPI.", domainName, ex);
			return null;
		}
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
			LOG.warn("Failed to check e-mail domain '{}': {}", domainName, response);

			if (cntRemaining.isPresent()) {
				long cnt = cntRemaining.getAsLong();
				if (cnt == 0) {
					OptionalLong secondsDelay = response.headers().firstValueAsLong("x-ratelimit-requests-reset");
					if (secondsDelay.isPresent()) {
						long seconds = secondsDelay.getAsLong();
						_pauseUntil = System.currentTimeMillis() + seconds * 1000;

						LOG.warn("Quota exceeded, pausing for {} seconds.", seconds);
					}
				}
			}

			return null;
		}

		RapidAPIResult result = RapidAPIResult.readRapidAPIResult(new JsonReader(new ReaderAdapter(new StringReader(response.body()))));

		LOG.info("Checked new e-mail domain '{}' (quota left {}): {}", domainName, cntRemaining.orElse(-1),
				result.isDisposable() ? "DISPOSABLE" : "OK");

		return result;
	}

	/**
	 * Converts a {@link RapidAPIResult} to a {@link DomainCheck}.
	 */
	static DomainCheck toDomainCheck(RapidAPIResult result) {
		// Parse "2024-02-07T17:09:29+01:00"
		String lastChangedString = result.getLastChanged();

		long lastChangedMillis;
		DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
		try {
			Date lastChanged = fmt.parse(lastChangedString);
			lastChangedMillis = lastChanged.getTime();
		} catch (ParseException e) {
			LOG.error("Failed to parse lastChanged result: {}", lastChangedString);
			lastChangedMillis = 0;
		}

		return DomainCheck.create()
				.setDomainName(result.getDomainName())
				.setDisposable(result.isDisposable())
				.setLastChanged(lastChangedMillis)
				.setSourceSystem(1)
				.setMxHost(result.getMxHost())
				.setMxIP(result.getMxIP());
	}

}
