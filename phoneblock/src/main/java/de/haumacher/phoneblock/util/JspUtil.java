/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.util;

/**
 * Utilities for safe content generation on JSP pages.
 */
public class JspUtil {

	public static String quote(Object value) {
		if (value == null) {
			return "";
		}
		
		return value.toString()
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&apos;")
				;
	}
	
}
