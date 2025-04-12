package de.haumacher.phoneblock.app.render;

import java.util.Locale;

public class Language {

	public final String tag;
	public final String flag;
	public final String label;
	public final Locale locale;

	public Language(String tag, String flag, String label) {
		this.tag = tag;
		this.flag = flag;
		this.label = label;
		this.locale = Locale.forLanguageTag(tag);
	}

	public static Language lang(String tag, String icon, String name) {
		return new Language(tag, icon, name);
	}

}
