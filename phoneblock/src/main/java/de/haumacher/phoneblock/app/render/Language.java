package de.haumacher.phoneblock.app.render;

public class Language {

	public final String tag;
	public final String flag;
	public final String label;

	public Language(String tag, String flag, String label) {
		this.tag = tag;
		this.flag = flag;
		this.label = label;
	}

	public static Language lang(String locale, String icon, String name) {
		return new Language(locale, icon, name);
	}

}
