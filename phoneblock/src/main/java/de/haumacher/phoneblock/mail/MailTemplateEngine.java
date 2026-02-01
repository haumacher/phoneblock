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

import de.haumacher.phoneblock.shared.Language;

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
	 * @param language The language for template resolution
	 * @param templateName The template name without path prefix or suffix (e.g., "mail-template")
	 * @param variables The template variables
	 * @return The processed HTML content
	 */
	public String processTemplate(Language language, String templateName, Map<String, Object> variables) {
		String templatePath = resolveTemplatePath(language, templateName);

		TemplateEngine templateEngine = buildTemplateEngine();
		Context context = new Context();
		context.setVariables(variables);

		return templateEngine.process(templatePath, context);
	}

	/**
	 * Resolves the template path for the given language, with fallback to German.
	 */
	private String resolveTemplatePath(Language language, String templateName) {
		String templatePath = language.tag + "/" + templateName;
		if (templateExists(templatePath)) {
			return templatePath;
		}

		if (!"de".equals(language.tag)) {
			String fallbackPath = "de/" + templateName;
			if (templateExists(fallbackPath)) {
				LOG.warn("Mail template not found: {}, falling back to German", templatePath);
				return fallbackPath;
			}
		}

		throw new RuntimeException("Mail template not found: " + templatePath);
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
		return processTemplate(Language.fromTag(locale), templateName, variables);
	}

	/**
	 * Checks if a template exists.
	 *
	 * @param templatePath The template path (e.g., "de/mobile-welcome-mail")
	 * @return {@code true} if the template exists, {@code false} otherwise
	 */
	private boolean templateExists(String templatePath) {
		String resourcePath = "templates/" + templatePath + ".html";
		try (var stream = MailTemplateEngine.class.getResourceAsStream(resourcePath)) {
			return stream != null;
		} catch (Exception ex) {
			return false;
		}
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
