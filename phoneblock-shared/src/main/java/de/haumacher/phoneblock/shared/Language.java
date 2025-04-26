package de.haumacher.phoneblock.shared;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class Language {
	
	private static final Language[] LANGUAGES = {
		lang("ar"     , "arab"  , "Arabic"                , Direction.rtl),
//		lang("bg"     , "bg"    , "Bulgarian"             , Direction.ltr),
//		lang("cs"     , "cz"    , "Czech"                 , Direction.ltr),
	    lang("da"     , "dk"    , "Danish"                , Direction.ltr),
	    lang("de"     , "de"    , "German"                , Direction.ltr),
	    lang("el"     , "gr"    , "Greek"                 , Direction.ltr),
//		lang("en-GB"  , "gb"    , "English (British)"     , Direction.ltr),
	    lang("en-US"  , "us"    , "English"               , Direction.ltr, "en"),
	    lang("es"     , "es"    , "Spanish"               , Direction.ltr),
//		lang("et"     , "et"    , "Estonian"              , Direction.ltr),
//		lang("fi"     , "fi"    , "Finnish"               , Direction.ltr),
	    lang("fr"     , "fr"    , "French"                , Direction.ltr),
//		lang("hu"     , "hu"    , "Hungarian"             , Direction.ltr),
//		lang("id"     , "id"    , "Indonesian"            , Direction.ltr),
	    lang("it"     , "it"    , "Italian"               , Direction.ltr),
//		lang("ja"     , "jp"    , "Japanese"              , Direction.ltr),
//		lang("ko"     , "kr"    , "Korean"                , Direction.ltr),
//		lang("lt"     , "lt"    , "Lithuanian"            , Direction.ltr),
//		lang("lv"     , "lv"    , "Latvian"               , Direction.ltr),
	    lang("nb"     , "no"    , "Norwegian"             , Direction.ltr),
	    lang("nl"     , "nl"    , "Dutch"                 , Direction.ltr),
	    lang("pl"     , "pl"    , "Polish"                , Direction.ltr),
//		lang("pt-BR"  , "br"    , "Portuguese (Brazilian)", Direction.ltr),
//		lang("pt-PT"  , "pt"    , "Portuguese"            , Direction.ltr),
//		lang("ro"     , "ro"    , "Romanian"              , Direction.ltr),
//		lang("ru"     , "ru"    , "Russian"               , Direction.ltr),
//		lang("sk"     , "sk"    , "Slovak"                , Direction.ltr),
//		lang("sl"     , "sl"    , "Slovenian"             , Direction.ltr),
	    lang("sv"     , "sv"    , "Swedish"               , Direction.ltr),
//		lang("tr"     , "tr"    , "Turkish"               , Direction.ltr),
	    lang("uk"     , "ua"    , "Ukrainian"             , Direction.ltr),
	    lang("zh-Hans", "cn"    , "Chinese"               , Direction.ltr, "zh"),
	};
	
	private static final Map<String, Language> LANG_BY_TAG = Arrays.stream(LANGUAGES).collect(Collectors.toMap(l -> l.tag, l -> l));
	private static final Map<Locale, Language> LANG_BY_LOCALE = Arrays.stream(LANGUAGES).collect(Collectors.toMap(l -> Locale.forLanguageTag(l.tag), l -> l));
	static {
		for (Language lang : LANGUAGES) {
			for (Locale fallback : lang.fallbacks) {
				LANG_BY_LOCALE.put(fallback, lang);
			}
		}
	}
	
	private static final Language DEFAULT_LANG = LANG_BY_TAG.get("en-US");
	
	public final String tag;
	public final String flag;
	public final String label;
	public final Locale locale;
	public final Direction direction;
	public final Locale[] fallbacks;

	public Language(String tag, String flag, String label, Direction direction, String[] fallbacks) {
		this.tag = tag;
		this.flag = flag;
		this.label = label;
		this.direction = direction;
		this.locale = Locale.forLanguageTag(tag);
		this.fallbacks = new Locale[fallbacks.length];
		for (int n = 0, cnt = fallbacks.length; n < cnt; n++) {
			this.fallbacks[n] = Locale.forLanguageTag(fallbacks[n]);
		}
	}

	public static Language[] all() {
		return LANGUAGES;
	}

	public static Language getDefault() {
		return DEFAULT_LANG;
	}

	public static Language fromTag(String tag) {
		return LANG_BY_TAG.get(tag);
	}

	public static Language fromLocale(Locale locale) {
		return LANG_BY_LOCALE.get(locale);
	}

	public static Language lang(String tag, String icon, String name, Direction direct, String... fallbacks) {
		return new Language(tag, icon, name, direct, fallbacks);
	}

	public static Collection<Locale> supportedLocales() {
		return LANG_BY_LOCALE.keySet();
	}

}
