package de.haumacher.phoneblock.app.render;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LocaleMap {
	
	private final String defaultLocale;
	private final Map<String, String> defaultLocaleByLang = new HashMap<>();
	private final Map<String, Map<String, String>> localeByLangAndCountry = new HashMap<>();

	public LocaleMap(String... locales) {
		defaultLocale = locales[0];
		for (String locale : locales) {
			int separator = locale.indexOf('-');
			if (separator < 0) {
				String lang = locale;
				localeByLangAndCountry.computeIfAbsent(lang, x -> new HashMap<>());
				defaultLocaleByLang.putIfAbsent(lang, locale);
			} else {
				String lang = locale.substring(0, separator);
				String country = locale.substring(separator + 1);
			
				localeByLangAndCountry.computeIfAbsent(lang, x -> new HashMap<>()).put(country, locale);
				defaultLocaleByLang.putIfAbsent(lang, locale);
			}
		}
	}
	
	public String getSupportedLocale(Locale locale) {
		String lang = locale.getLanguage();
		if (lang == null) {
			return defaultLocale;
		}
		
		String country = locale.getCountry();
		if (country == null) {
			return defaultLocaleByLang.get(lang);
		}
		
		Map<String, String> localeByCountry = localeByLangAndCountry.get(lang);
		if (localeByCountry == null) {
			return null;
		}

		String result = localeByCountry.get(country);
		if (result != null) {
			return result;
		}
		
		return defaultLocaleByLang.get(lang);
	}
	
	public String getDefaultLocale() {
		return defaultLocale;
	}

}
