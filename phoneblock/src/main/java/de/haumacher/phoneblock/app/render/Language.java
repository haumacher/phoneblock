package de.haumacher.phoneblock.app.render;

public class Language {

	public final String locale;
	public final String flag;
	public final String name;

	public Language(String locale, String flag, String name) {
		this.locale = locale;
		this.flag = flag;
		this.name = name;
	}

	public static Language lang(String locale, String icon, String name) {
		return new Language(locale, icon, name);
	}

}
