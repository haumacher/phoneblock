/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.util;

/**
 * TODO
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public class JspUtil {

	public static String quote(String s) {
		return s
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("&", "&amp;");
	}
	
}
