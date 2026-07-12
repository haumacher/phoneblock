/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.translate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;

import com.deepl.api.TextTranslationOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.haumacher.phoneblock.shared.Language;

/**
 * Translates the {@code DIAG_TEMPLATE} mail templates into every supported locale
 * via DeepL, driving the diagnostics REST API — never touching the server or its
 * database directly.
 *
 * <p>Reuses the build's translation machinery: the same DeepL SDK
 * ({@code com.deepl.api}) the {@code auto-translate} plugin wraps, and the same
 * {@code settings.xml} key source (a {@code <server>} entry). The target
 * languages are derived from {@link Language#all()} — the one source of truth —
 * so they are never a hand-copied list that could drift.</p>
 *
 * <p>For each template key, the {@code sourceLang} (default {@code de}) row is the
 * master; its subject and (HTML) body are translated into each other locale and
 * written back via {@code POST /admin/diag/templates}. Placeholders like
 * {@code {deviceId}} are shielded from translation; the body is translated with
 * DeepL HTML tag handling so markup is preserved.</p>
 *
 * <p>Both secrets come from {@code settings.xml} by default (so they never appear
 * on the command line or in shell history):</p>
 * <pre>
 *   &lt;server&gt;&lt;id&gt;deepl&lt;/id&gt;&lt;password&gt;YOUR_DEEPL_KEY&lt;/password&gt;&lt;/server&gt;
 *   &lt;server&gt;&lt;id&gt;phoneblock-admin&lt;/id&gt;&lt;password&gt;YOUR_API_TOKEN&lt;/password&gt;&lt;/server&gt;
 * </pre>
 *
 * <p>Example: {@code mvn -Pwith-deepl
 * de.haumacher:phoneblock-translate-maven-plugin:translate-diag-templates
 * -Dtranslate.apiUrl=https://phoneblock.net/pb-test/api -Dtranslate.dryRun=true}</p>
 */
@Mojo(name = "translate-diag-templates", requiresProject = false, threadSafe = true)
public class TranslateDiagTemplatesMojo extends AbstractMojo {

	/** Base URL of the PhoneBlock API, e.g. {@code https://phoneblock.net/phoneblock/api}. */
	@Parameter(property = "translate.apiUrl", required = true)
	private String apiUrl;

	/** {@code settings.xml} server id holding the DeepL API key (as its password). */
	@Parameter(property = "translate.serverId", defaultValue = "deepl")
	private String serverId;

	/** DeepL API key given directly (takes precedence over {@link #serverId}). */
	@Parameter(property = "translate.apiKey")
	private String apiKey;

	/** {@code settings.xml} server id holding the PhoneBlock bearer token (as its password). */
	@Parameter(property = "translate.tokenServerId", defaultValue = "phoneblock-admin")
	private String tokenServerId;

	/** PhoneBlock bearer token given directly (takes precedence over {@link #tokenServerId}). */
	@Parameter(property = "translate.token")
	private String token;

	/** The master language every template is translated from. */
	@Parameter(property = "translate.sourceLang", defaultValue = "de")
	private String sourceLang;

	/** Re-translate and overwrite locales that already exist (default: only fill gaps). */
	@Parameter(property = "translate.overwrite", defaultValue = "false")
	private boolean overwrite;

	/** Translate and report, but do not write anything back. */
	@Parameter(property = "translate.dryRun", defaultValue = "false")
	private boolean dryRun;

	@Parameter(defaultValue = "${settings}", readonly = true, required = true)
	private Settings settings;

	@Component
	private SettingsDecrypter settingsDecrypter;

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{[A-Za-z0-9_]+\\}");

	private final HttpClient _http = HttpClient.newHttpClient();
	private final ObjectMapper _json = new ObjectMapper();

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		String deeplKey = resolveSecret(apiKey, serverId, "DeepL API key");
		String bearer = resolveSecret(token, tokenServerId, "PhoneBlock API token");
		String base = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;

		List<String> targets = new ArrayList<>();
		for (Language l : Language.all()) {
			if (!l.tag.equals(sourceLang)) {
				targets.add(l.tag);
			}
		}
		getLog().info("Target locales (from Language.all()): " + targets);

		// key -> (lang -> template)
		Map<String, Map<String, Template>> byKey = fetchTemplates(base, bearer);

		com.deepl.api.Translator deepl = new com.deepl.api.Translator(deeplKey);

		int stored = 0, skipped = 0, noSource = 0, failed = 0;
		for (Map.Entry<String, Map<String, Template>> e : byKey.entrySet()) {
			String key = e.getKey();
			Template src = e.getValue().get(sourceLang);
			if (src == null) {
				getLog().warn("Template '" + key + "' has no '" + sourceLang + "' source — skipping.");
				noSource++;
				continue;
			}
			for (String tgt : targets) {
				if (!overwrite && e.getValue().containsKey(tgt)) {
					skipped++;
					continue;
				}
				String subject, body;
				try {
					subject = translate(deepl, src.subject(), tgt, false);
					body = translate(deepl, src.body(), tgt, true);
				} catch (Exception ex) {
					getLog().warn("DeepL failed for " + key + " -> " + tgt + ": " + ex.getMessage() + " — skipping.");
					failed++;
					continue;
				}
				if (dryRun) {
					getLog().info("[dry-run] " + key + " -> " + tgt + " | subject: " + subject);
				} else {
					postTemplate(base, bearer, key, tgt, subject, body);
					getLog().info("Stored " + key + " -> " + tgt);
				}
				stored++;
			}
		}
		getLog().info(String.format("Done%s: %d translated, %d already present, %d without source, %d failed.",
			dryRun ? " (dry-run, nothing written)" : "", stored, skipped, noSource, failed));
	}

	/** Translates one text, shielding {@code {placeholder}} tokens; HTML tag handling for bodies. */
	private String translate(com.deepl.api.Translator deepl, String text, String targetTag, boolean html)
			throws Exception {
		if (text == null || text.isBlank()) {
			return text;
		}
		Protected p = protect(text);
		TextTranslationOptions opts = new TextTranslationOptions();
		if (html) {
			opts.setTagHandling("html");
		}
		String out = deepl.translateText(p.masked(), deeplSource(sourceLang), deeplTarget(targetTag), opts).getText();
		return restore(out, p);
	}

	// ---- DeepL language codes (the Language tags are DeepL codes already, bar one) ----

	private static String deeplTarget(String tag) {
		// DeepL uses "zh" for simplified Chinese; every other tag is a valid DeepL
		// target code as-is (da, el, en-US, nb, uk, ...).
		return "zh-Hans".equals(tag) ? "zh" : tag;
	}

	private static String deeplSource(String tag) {
		// DeepL source codes carry no region ("en", not "en-US"); simplified Chinese is "zh".
		if ("zh-Hans".equals(tag)) {
			return "zh";
		}
		int dash = tag.indexOf('-');
		return dash < 0 ? tag : tag.substring(0, dash);
	}

	// ---- Placeholder shielding ----

	private record Protected(String masked, List<String> tokens) {}

	private static Protected protect(String text) {
		List<String> tokens = new ArrayList<>();
		Matcher m = PLACEHOLDER.matcher(text);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			int idx = tokens.size();
			tokens.add(m.group());
			// Private-use sentinels DeepL treats as opaque and leaves untouched.
			m.appendReplacement(sb, Matcher.quoteReplacement("" + idx + ""));
		}
		m.appendTail(sb);
		return new Protected(sb.toString(), tokens);
	}

	private static String restore(String text, Protected p) {
		String s = text;
		for (int i = 0; i < p.tokens().size(); i++) {
			s = s.replace("" + i + "", p.tokens().get(i));
		}
		return s;
	}

	// ---- REST API ----

	private record Template(String templateKey, String lang, String subject, String body) {}

	private Map<String, Map<String, Template>> fetchTemplates(String base, String bearer)
			throws MojoExecutionException {
		HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/admin/diag/templates"))
			.header("Authorization", "Bearer " + bearer)
			.header("Accept", "application/json")
			.GET().build();
		try {
			HttpResponse<String> resp = _http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (resp.statusCode() != 200) {
				throw new MojoExecutionException("GET templates failed: HTTP " + resp.statusCode() + " " + resp.body());
			}
			Map<String, Map<String, Template>> byKey = new LinkedHashMap<>();
			for (JsonNode n : _json.readTree(resp.body())) {
				Template t = new Template(text(n, "templateKey"), text(n, "lang"), text(n, "subject"), text(n, "body"));
				byKey.computeIfAbsent(t.templateKey(), k -> new LinkedHashMap<>()).put(t.lang(), t);
			}
			return byKey;
		} catch (MojoExecutionException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new MojoExecutionException("GET templates failed: " + ex.getMessage(), ex);
		}
	}

	private void postTemplate(String base, String bearer, String key, String lang, String subject, String body)
			throws MojoExecutionException {
		ObjectNode payload = _json.createObjectNode();
		payload.put("templateKey", key);
		payload.put("lang", lang);
		payload.put("subject", subject);
		payload.put("body", body);
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/admin/diag/templates"))
				.header("Authorization", "Bearer " + bearer)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(_json.writeValueAsString(payload), StandardCharsets.UTF_8))
				.build();
			HttpResponse<String> resp = _http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (resp.statusCode() != 200) {
				throw new MojoExecutionException("POST template " + key + "/" + lang
					+ " failed: HTTP " + resp.statusCode() + " " + resp.body());
			}
		} catch (MojoExecutionException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new MojoExecutionException("POST template " + key + "/" + lang + " failed: " + ex.getMessage(), ex);
		}
	}

	private static String text(JsonNode n, String field) {
		JsonNode v = n.get(field);
		return v == null || v.isNull() ? null : v.asText();
	}

	// ---- Secrets ----

	private String resolveSecret(String direct, String serverId, String label) throws MojoFailureException {
		if (direct != null && !direct.isBlank()) {
			return direct;
		}
		Server server = settings.getServer(serverId);
		if (server == null) {
			throw new MojoFailureException("No " + label + ": add <server><id>" + serverId
				+ "</id><password>…</password></server> to settings.xml, or pass it directly.");
		}
		SettingsDecryptionResult result = settingsDecrypter.decrypt(new DefaultSettingsDecryptionRequest(server));
		Server decrypted = result.getServer();
		// Accept the secret in <password> or <passphrase> — the existing 'deepl'
		// server (shared with the auto-translate plugin) keeps it in <passphrase>.
		String secret = decrypted.getPassword();
		if (secret == null || secret.isBlank()) {
			secret = decrypted.getPassphrase();
		}
		if (secret == null || secret.isBlank()) {
			throw new MojoFailureException("The '" + serverId + "' server in settings.xml has no "
				+ "<password> or <passphrase> (" + label + ").");
		}
		return secret;
	}
}
