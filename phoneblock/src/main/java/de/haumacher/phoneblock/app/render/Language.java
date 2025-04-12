package de.haumacher.phoneblock.app.render;

import java.util.Locale;

public class Language {

	public final String tag;
	public final String flag;
	public final String label;
	public final Locale locale;
	public final Locale[] fallbacks;

	public Language(String tag, String flag, String label, String[] fallbacks) {
		this.tag = tag;
		this.flag = flag;
		this.label = label;
		this.locale = Locale.forLanguageTag(tag);
		this.fallbacks = new Locale[fallbacks.length];
		for (int n = 0, cnt = fallbacks.length; n < cnt; n++) {
			this.fallbacks[n] = Locale.forLanguageTag(fallbacks[n]);
		}
	}

	public static Language lang(String tag, String icon, String name, String... fallbacks) {
		return new Language(tag, icon, name, fallbacks);
	}

}
