/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.mail;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;

/**
 * A Thymeleaf template resolver that uses {@link Class#getResourceAsStream(String)} instead of
 * {@link ClassLoader#getResource(String)} for loading templates.
 *
 * <p>
 * This is necessary for compatibility with the Java Module System (JPMS), where
 * {@link ClassLoader#getResource(String)} may not work for resources within the module,
 * but {@link Class#getResourceAsStream(String)} does.
 * </p>
 */
public class ClassResourceTemplateResolver extends AbstractConfigurableTemplateResolver {

	private final Class<?> _resourceClass;

	/**
	 * Creates a {@link ClassResourceTemplateResolver}.
	 *
	 * @param resourceClass The class whose {@link Class#getResourceAsStream(String)} method will be used
	 *                      to load template resources. The resource path is relative to this class's package.
	 */
	public ClassResourceTemplateResolver(Class<?> resourceClass) {
		_resourceClass = resourceClass;
	}

	@Override
	protected ITemplateResource computeTemplateResource(IEngineConfiguration configuration, String ownerTemplate,
			String template, String resourceName, String characterEncoding,
			Map<String, Object> templateResolutionAttributes) {

		Charset charset = characterEncoding != null ? Charset.forName(characterEncoding) : StandardCharsets.UTF_8;
		return new ClassTemplateResource(_resourceClass, resourceName, charset);
	}

	/**
	 * Template resource implementation that uses {@link Class#getResourceAsStream(String)}.
	 */
	private static class ClassTemplateResource implements ITemplateResource {

		private final Class<?> _resourceClass;
		private final String _resourceName;
		private final Charset _charset;

		ClassTemplateResource(Class<?> resourceClass, String resourceName, Charset charset) {
			_resourceClass = resourceClass;
			_resourceName = resourceName;
			_charset = charset;
		}

		@Override
		public String getDescription() {
			return "ClassResource[" + _resourceClass.getName() + ", " + _resourceName + "]";
		}

		@Override
		public String getBaseName() {
			int lastSlash = _resourceName.lastIndexOf('/');
			if (lastSlash >= 0) {
				String fileName = _resourceName.substring(lastSlash + 1);
				int lastDot = fileName.lastIndexOf('.');
				if (lastDot >= 0) {
					return fileName.substring(0, lastDot);
				}
				return fileName;
			}
			int lastDot = _resourceName.lastIndexOf('.');
			if (lastDot >= 0) {
				return _resourceName.substring(0, lastDot);
			}
			return _resourceName;
		}

		@Override
		public boolean exists() {
			try (InputStream in = _resourceClass.getResourceAsStream(_resourceName)) {
				return in != null;
			} catch (IOException e) {
				return false;
			}
		}

		@Override
		public Reader reader() throws IOException {
			InputStream inputStream = _resourceClass.getResourceAsStream(_resourceName);
			if (inputStream == null) {
				throw new IOException("Resource not found: " + _resourceName + " (relative to " + _resourceClass.getName() + ")");
			}

			return new InputStreamReader(new BufferedInputStream(inputStream), _charset);
		}

		@Override
		public ITemplateResource relative(String relativeLocation) {
			// Compute the relative path
			int lastSlash = _resourceName.lastIndexOf('/');
			String basePath = lastSlash >= 0 ? _resourceName.substring(0, lastSlash + 1) : "";
			String newResourceName = basePath + relativeLocation;

			return new ClassTemplateResource(_resourceClass, newResourceName, _charset);
		}
	}
}
