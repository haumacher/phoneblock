package de.haumacher.phoneblock.app.render;

import static de.haumacher.phoneblock.app.render.Language.lang;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.Properties;
import java.util.Locale.LanguageRange;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.servlet.IServletWebExchange;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.LoginServlet;
import de.haumacher.phoneblock.app.RegistrationServlet;
import de.haumacher.phoneblock.app.UIProperties;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public class DefaultController implements WebController {
	
	private static final Language[] LANGUAGES = {
		lang("ar"     , "arab"  , "Arabic"                ),
//	    lang("bg"     , "bg"    , "Bulgarian"             ),
//	    lang("cs"     , "cz"    , "Czech"                 ),
	    lang("da"     , "dk"    , "Danish"                ),
	    lang("de"     , "de"    , "German"                ),
	    lang("el"     , "gr"    , "Greek"                 ),
//	    lang("en-GB"  , "gb"    , "English (British)"     ),
	    lang("en-US"  , "us"    , "English (American)"    ),
	    lang("es"     , "es"    , "Spanish"               ),
//	    lang("et"     , "et"    , "Estonian"              ),
//	    lang("fi"     , "fi"    , "Finnish"               ),
	    lang("fr"     , "fr"    , "French"                ),
//	    lang("hu"     , "hu"    , "Hungarian"             ),
//	    lang("id"     , "id"    , "Indonesian"            ),
	    lang("it"     , "it"    , "Italian"               ),
//	    lang("ja"     , "jp"    , "Japanese"              ),
//	    lang("ko"     , "kr"    , "Korean"                ),
//	    lang("lt"     , "lt"    , "Lithuanian"            ),
//	    lang("lv"     , "lv"    , "Latvian"               ),
	    lang("nb"     , "no"    , "Norwegian Bokmål"      ),
	    lang("nl"     , "nl"    , "Dutch"                 ),
	    lang("pl"     , "pl"    , "Polish"                ),
//	    lang("pt-BR"  , "br"    , "Portuguese (Brazilian)"),
//	    lang("pt-PT"  , "pt"    , "Portuguese"            ),
//	    lang("ro"     , "ro"    , "Romanian"              ),
//	    lang("ru"     , "ru"    , "Russian"               ),
//	    lang("sk"     , "sk"    , "Slovak"                ),
//	    lang("sl"     , "sl"    , "Slovenian"             ),
	    lang("sv"     , "sv"    , "Swedish"               ),
//	    lang("tr"     , "tr"    , "Turkish"               ),
	    lang("uk"     , "ua"    , "Ukrainian"             ),
	    lang("zh-Hans", "cn"    , "Chinese"               ),
	};
	
	private static final Map<String, Language> LANG_BY_TAG = Arrays.stream(LANGUAGES).collect(Collectors.toMap(l -> l.tag, l -> l));
	private static final Map<Locale, Language> LANG_BY_LOCALE = Arrays.stream(LANGUAGES).collect(Collectors.toMap(l -> Locale.forLanguageTag(l.tag), l -> l));
	private static final Language DEFAULT_LANG = LANGUAGES[0];

	/**
	 * Template resolution attribute specifying the requested language.
	 */
	public static final String LANG_ATTR = "lang";

	static final String RENDER_TEMPLATE = "renderTemplate";

	private static final Logger LOG = LoggerFactory.getLogger(DefaultController.class);

	public static final WebController INSTANCE = new DefaultController();
	
	private static final Map<String, Object> PROPS;
	private static final Map<String, Object> DEPS;

	private static final List<RatingDisplay> RATINGS = Arrays.asList(Rating.values()).stream()
		.map(r -> new RatingDisplay(r))
		.toList();
	
	static {
		PROPS = toModel(UIProperties.APP_PROPERTIES);

		DEPS = new HashMap<>();
		DEPS.put("bulma", UIProperties.BULMA_PATH);
		DEPS.put("bulmaCalendar", UIProperties.BULMA_CALENDAR_PATH);
		DEPS.put("bulmaCollapsible", UIProperties.BULMA_COLLAPSIBLE_PATH);
		DEPS.put("chartjs", UIProperties.CHARTJS_PATH);
		DEPS.put("fontawesome", UIProperties.FA_PATH);
		DEPS.put("jquery", UIProperties.JQUERY_PATH);
		DEPS.put("swagger", UIProperties.SWAGGER_PATH);
		DEPS.put("flags", UIProperties.FLAGS_PATH);
	}

	@Override
	public void process(IServletWebExchange webExchange, ITemplateEngine templateEngine, Writer writer) throws IOException {
		HttpServletRequest request = (HttpServletRequest) webExchange.getNativeRequestObject();
		
        Language lang = selectLanguage(request);
        WebContext ctx = new WebContext(webExchange, lang.locale);
        
        fillContext(ctx, request);

        String template = (String) request.getAttribute(RENDER_TEMPLATE);
        if (template == null) {
        	String path = request.getServletPath();
        	String prefix = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        	template = prefix.isEmpty() ? "/index" : prefix;

        	LOG.debug("Serving template {} for {}.", template, path);
        }

        request.setAttribute("currentLang", lang);
        
		// Note: Template is required to start with "/".
		String i18nTemplate = "/" + lang.tag + template;
        
		templateEngine.process(i18nTemplate, ctx, writer);
	}

	public static Language selectLanguage(HttpServletRequest request) {
		String selectedLang = request.getParameter("lang");
		Language lang;
        if (selectedLang == null) {
        	HttpSession session = request.getSession(false);
        	if (session != null) {
        		lang = (Language) session.getAttribute("lang");
        		if (lang != null) {
        			return lang;
        		}
        	}
        	
    		// Use browser default
    		List<LanguageRange> acceptLanguage = LanguageRange.parse(request.getHeader("Accept-Language"));
    		Locale locale = Locale.lookup(acceptLanguage, LANG_BY_LOCALE.keySet());
    		if (locale == null) {
    			lang = DEFAULT_LANG;
    		} else {
    			lang = LANG_BY_LOCALE.get(locale);
    		}
    		
    		if (session != null) {
    			// Remember to make next lookup more efficient.
    			session.setAttribute("lang", lang);
    		}
        } else {
        	// Normalize value.
        	Language language = LANG_BY_TAG.get(selectedLang);
        	if (language == null) {
        		lang = DEFAULT_LANG;
        	} else {
        		lang = language;
        	}

    		// Remember requested language.
        	request.getSession().setAttribute("lang", lang);
        }

        return lang;
	}

	protected void fillContext(WebContext ctx, HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		String userName = LoginFilter.getAuthenticatedUser(session);
  		String token = RegistrationServlet.getPassword(session);
		
        ctx.setVariable("userName", userName);
        ctx.setVariable("token", token);
        ctx.setVariable("supporterId", userName == null ? null : "PhoneBlock-" + userName.substring(0, 13));
        ctx.setVariable("loggedIn", Boolean.valueOf(userName != null));
        ctx.setVariable(LoginServlet.LOCATION_ATTRIBUTE, LoginServlet.location(request));
        ctx.setVariable("currentPage", ServletUtil.currentPage(request));
        
        ctx.setVariable("titleKey", "app.defaultTitle");
        ctx.setVariable("descriptionKey", "app.defaultDescription");
        ctx.setVariable("keywords", "");
        
		ctx.setVariable("canonical", "https://phoneblock.net" + request.getContextPath() + request.getServletPath());
        ctx.setVariable("props", PROPS);
        ctx.setVariable("deps", DEPS);
        ctx.setVariable("contextPath", request.getContextPath());
        
    	String userAgent = request.getHeader("User-Agent");
    	userAgent = userAgent == null ? "" : userAgent.toLowerCase();
    	boolean android = userAgent.contains("android");
    	boolean iphone = userAgent.contains("iPhone");
    	
    	ctx.setVariable("android", Boolean.valueOf(android));
		ctx.setVariable("iphone", Boolean.valueOf(iphone));
		ctx.setVariable("fritzbox", Boolean.valueOf(!android && !iphone));
		
		ctx.setVariable("ratings", RATINGS);
		ctx.setVariable("languages", LANGUAGES);
	}

	
	/**
	 * Converts {@link Properties} with '.'-structured keys to nested maps. 
	 */
	static Map<String, Object> toModel(Properties appProperties) {
		Map<String, Object> result = new HashMap<>();
		
		for (Entry<Object, Object> entry : appProperties.entrySet()) {
			String key = (String) entry.getKey();
			
			Map<String, Object> parent = result;
			String last = null;
			for (String part : key.split("\\.")) {
				if (last != null) {
					@SuppressWarnings("unchecked")
					Map<String, Object> inner = (Map<String, Object>) parent.get(last);
					if (inner == null) {
						inner = new HashMap<>();
					}
					parent.put(last, inner);
					
					parent = inner;
				}
				
				last = part;
			}
			
			parent.put(last, entry.getValue());
		}
		
		return result;
	}

}
