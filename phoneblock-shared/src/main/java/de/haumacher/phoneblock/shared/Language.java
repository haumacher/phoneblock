package de.haumacher.phoneblock.shared;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class Language {
	
	private static final Language[] LANGUAGES = {
		lang("ar"     , "arab"  , "Arabic"                , "+966", Direction.rtl),
//		lang("bg"     , "bg"    , "Bulgarian"             , "+", Direction.ltr),
//		lang("cs"     , "cz"    , "Czech"                 , "+", Direction.ltr),
	    lang("da"     , "dk"    , "Danish"                , "+45", Direction.ltr),
	    lang("de"     , "de"    , "German"                , "+49", Direction.ltr),
	    lang("el"     , "gr"    , "Greek"                 , "+30", Direction.ltr),
//		lang("en-GB"  , "gb"    , "English (British)"     , "+", Direction.ltr),
	    lang("en-US"  , "us"    , "English"               , "+1", Direction.ltr, "en"),
	    lang("es"     , "es"    , "Spanish"               , "+34", Direction.ltr),
//		lang("et"     , "et"    , "Estonian"              , "+", Direction.ltr),
//		lang("fi"     , "fi"    , "Finnish"               , "+", Direction.ltr),
	    lang("fr"     , "fr"    , "French"                , "+33", Direction.ltr),
//		lang("hu"     , "hu"    , "Hungarian"             , "+", Direction.ltr),
//		lang("id"     , "id"    , "Indonesian"            , "+", Direction.ltr),
	    lang("it"     , "it"    , "Italian"               , "+39", Direction.ltr),
//		lang("ja"     , "jp"    , "Japanese"              , "+", Direction.ltr),
//		lang("ko"     , "kr"    , "Korean"                , "+", Direction.ltr),
//		lang("lt"     , "lt"    , "Lithuanian"            , "+", Direction.ltr),
//		lang("lv"     , "lv"    , "Latvian"               , "+", Direction.ltr),
	    lang("nb"     , "no"    , "Norwegian"             , "+47", Direction.ltr),
	    lang("nl"     , "nl"    , "Dutch"                 , "+31", Direction.ltr),
	    lang("pl"     , "pl"    , "Polish"                , "+48", Direction.ltr),
//		lang("pt-BR"  , "br"    , "Portuguese (Brazilian)", "+", Direction.ltr),
//		lang("pt-PT"  , "pt"    , "Portuguese"            , "+", Direction.ltr),
//		lang("ro"     , "ro"    , "Romanian"              , "+", Direction.ltr),
//		lang("ru"     , "ru"    , "Russian"               , "+", Direction.ltr),
//		lang("sk"     , "sk"    , "Slovak"                , "+", Direction.ltr),
//		lang("sl"     , "sl"    , "Slovenian"             , "+", Direction.ltr),
	    lang("sv"     , "sv"    , "Swedish"               , "+46", Direction.ltr),
//		lang("tr"     , "tr"    , "Turkish"               , "+", Direction.ltr),
	    lang("uk"     , "ua"    , "Ukrainian"             , "+380", Direction.ltr),
	    lang("zh-Hans", "cn"    , "Chinese"               , "+86", Direction.ltr, "zh"),
	};
	
	private static final Map<String, Language> LANG_BY_TAG = Arrays.stream(LANGUAGES).collect(Collectors.toMap(l -> l.tag, l -> l));
	private static final Map<Locale, Language> LANG_BY_LOCALE = new HashMap<>();
	static {
		for (Language lang : LANGUAGES) {
			LANG_BY_LOCALE.put(lang.locale, lang);
			for (Locale fallback : lang.fallbacks) {
				LANG_BY_LOCALE.put(fallback, lang);
			}
		}
	}
	
	private static final Language DEFAULT_LANG = LANG_BY_TAG.get("en-US");
	
	public final String tag;
	public final String flag;
	public final String label;
	public final String dialPrefix;
	public final Locale locale;
	public final Direction direction;
	public final Locale[] fallbacks;

	public Language(String tag, String flag, String label, String dialPrefix, Direction direction, String[] fallbacks) {
		this.tag = tag;
		this.flag = flag;
		this.label = label;
		this.dialPrefix = dialPrefix;
		this.direction = direction;
		this.locale = Locale.forLanguageTag(tag);
		this.fallbacks = new Locale[fallbacks.length];
		for (int n = 0, cnt = fallbacks.length; n < cnt; n++) {
			this.fallbacks[n] = Locale.forLanguageTag(fallbacks[n]);
		}
	}
	
	@Override
	public String toString() {
		return this.tag;
	}

	public static Language[] all() {
		return LANGUAGES;
	}

	public static Language getDefault() {
		return DEFAULT_LANG;
	}

	/**
	 * Looks up a {@link Language} by tag, returning the default if not found.
	 *
	 * <p>
	 * This method first tries an exact tag match, then parses the tag as a locale
	 * and applies locale-based fallback logic (stripping region codes).
	 * </p>
	 *
	 * @param tag The language tag (e.g., "de", "de-DE", "en-US", "en-GB")
	 * @return The matching {@link Language}, or {@link #getDefault()} if not found or tag is null/empty
	 */
	public static Language fromTag(String tag) {
		if (tag == null || tag.isEmpty()) {
			return getDefault();
		}

		// Try exact tag match first
		Language lang = LANG_BY_TAG.get(tag);
		if (lang != null) {
			return lang;
		}

		// Try locale-based lookup with fallback
		return fromLocale(Locale.forLanguageTag(tag));
	}

	/**
	 * Looks up a {@link Language} by {@link Locale}, returning the default if not found.
	 *
	 * <p>
	 * Tries exact locale match first, then falls back to language-only lookup
	 * (stripping region code, e.g., "de-DE" → "de").
	 * </p>
	 *
	 * @param locale The locale to look up
	 * @return The matching {@link Language}, or {@link #getDefault()} if not found or locale is null
	 */
	public static Language fromLocale(Locale locale) {
		if (locale == null) {
			return getDefault();
		}

		Language lang = LANG_BY_LOCALE.get(locale);
		if (lang != null) {
			return lang;
		}

		// Try with language-only (strip region), e.g., "de-DE" -> "de"
		String language = locale.getLanguage();
		if (!language.isEmpty()) {
			Locale languageOnly = Locale.forLanguageTag(language);
			lang = LANG_BY_LOCALE.get(languageOnly);
			if (lang != null) {
				return lang;
			}
		}

		return getDefault();
	}

	public static Language lang(String tag, String icon, String name, String dialPrefix, Direction direct, String... fallbacks) {
		return new Language(tag, icon, name, dialPrefix, direct, fallbacks);
	}

	public static Collection<Locale> supportedLocales() {
		return LANG_BY_LOCALE.keySet();
	}

}
