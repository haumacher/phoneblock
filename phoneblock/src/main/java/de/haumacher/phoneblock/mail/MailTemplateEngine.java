/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.mail;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Thymeleaf template engine configured for email templates.
 *
 * <p>
 * Uses ClassLoaderTemplateResolver to load templates from classpath resources
 * in the mail package. Templates are organized by locale (e.g., templates/de/, templates/en-US/).
 * </p>
 */
public class MailTemplateEngine {

	private static final Logger LOG = LoggerFactory.getLogger(MailTemplateEngine.class);

	private static final MailTemplateEngine INSTANCE = new MailTemplateEngine();

	private MailTemplateEngine() {
	}

	/**
	 * Gets the singleton instance.
	 */
	public static MailTemplateEngine getInstance() {
		return INSTANCE;
	}

	/**
	 * Process a mail template with the given variables.
	 *
	 * @param locale The locale for template resolution (e.g., "de", "en-US")
	 * @param templateName The template name without path prefix or suffix (e.g., "mail-template")
	 * @param variables The template variables
	 * @return The processed HTML content
	 */
	public String processTemplate(String locale, String templateName, Map<String, Object> variables) {
		// Build template engine with current classloader context
		TemplateEngine templateEngine = buildTemplateEngine();

		Context context = new Context();
		context.setVariables(variables);

		// Try localized template first (e.g., "de-DE/template")
		String localizedTemplate = locale + "/" + templateName;
		try {
			return templateEngine.process(localizedTemplate, context);
		} catch (Exception ex) {
			// Template not found for exact locale
		}

		// Try language-only variant (e.g., "de-DE" -> "de")
		int dashIndex = locale.indexOf('-');
		if (dashIndex > 0) {
			String languageOnly = locale.substring(0, dashIndex);
			String languageTemplate = languageOnly + "/" + templateName;
			try {
				LOG.debug("Mail template not found for {}, trying {}", locale, languageOnly);
				return templateEngine.process(languageTemplate, context);
			} catch (Exception ex) {
				// Template not found for language-only locale
			}
		}

		// Final fallback to German
		if (!"de".equals(locale) && !locale.startsWith("de-")) {
			LOG.warn("Mail template not found: {}, falling back to German", localizedTemplate);
			String fallbackTemplate = "de/" + templateName;
			return templateEngine.process(fallbackTemplate, context);
		}

		throw new RuntimeException("Mail template not found: " + localizedTemplate);
	}

	private static TemplateEngine buildTemplateEngine() {
		// Use ClassResourceTemplateResolver for JPMS compatibility
		// Class.getResourceAsStream() works in JPMS while ClassLoader.getResource() doesn't
		ClassResourceTemplateResolver templateResolver = new ClassResourceTemplateResolver(MailTemplateEngine.class);

		templateResolver.setTemplateMode(TemplateMode.HTML);

		// Templates are relative to MailTemplateEngine class, in templates/{locale}/ subdirectory
		templateResolver.setPrefix("templates/");
		templateResolver.setSuffix(".html");

		// Character encoding
		templateResolver.setCharacterEncoding("UTF-8");

		// Cache templates for 1 hour
		templateResolver.setCacheTTLMs(3600000L);
		templateResolver.setCacheable(true);

		// Check existence before resolving
		templateResolver.setCheckExistence(true);

		TemplateEngine templateEngine = new TemplateEngine();
		templateEngine.setTemplateResolver(templateResolver);

		return templateEngine;
	}
}
