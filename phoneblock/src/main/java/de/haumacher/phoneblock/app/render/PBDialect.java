package de.haumacher.phoneblock.app.render;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.dialect.IExpressionObjectDialect;
import org.thymeleaf.expression.IExpressionObjectFactory;

/**
 * PhoneBlock-specific dialect for the Thymeleaf template engine.
 */
public class PBDialect implements IExpressionObjectDialect {
	private static final String CONVERTERS_NAME = "converters";
	protected static final Object CONVERTERS = new Converters();
	protected static final Set<String> NAMES = new HashSet<>(Arrays.asList(CONVERTERS_NAME));

	@Override
	public String getName() {
		return "pb";
	}

	@Override
	public IExpressionObjectFactory getExpressionObjectFactory() {
		return new IExpressionObjectFactory() {
			@Override
			public boolean isCacheable(String expressionObjectName) {
				return true;
			}
			
			@Override
			public Set<String> getAllExpressionObjectNames() {
				return NAMES;
			}
			
			@Override
			public Object buildObject(IExpressionContext context, String expressionObjectName) {
				switch (expressionObjectName) {
				case CONVERTERS_NAME: return CONVERTERS;
				}
				return null;
			}
		};
	}
}