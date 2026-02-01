package de.haumacher.phoneblock.app.render;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.servlet.IServletWebExchange;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.LoginServlet;
import de.haumacher.phoneblock.app.UIProperties;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.location.Countries;
import de.haumacher.phoneblock.location.LocationService;
import de.haumacher.phoneblock.location.model.Country;
import de.haumacher.phoneblock.shared.Language;
import de.haumacher.phoneblock.util.ServletUtil;
import de.haumacher.phoneblock.util.UserAgentType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class DefaultController implements WebController {
	
	/**
	 * Session attribute specifying the preferred language of type {@link Language}.
	 */
	public static final String LANG_ATTR = "lang";
	
	/**
	 * Session attribute specifying the user's dial prefix ("+49" for Germany).
	 *
	 * @see Country#getDialPrefix()
	 */
	public static final String DIAL_PREFIX_ATTR = "dialPrefix";

	/**
	 * Session attribute for storing a newly created password to display once.
	 */
	public static final String PASSWORD_ATTR = "passwd";

	/**
	 * Session attribute for storing a newly created CardDAV token to display once.
	 */
	public static final String CARD_DAV_TOKEN_ATTR = "cardDavToken";

	/**
	 * Session attribute for storing a newly created API key to display once.
	 */
	public static final String API_KEY_ATTR = "apiKey";

	/**
	 * Template variable for displaying a newly created password/token.
	 */
	public static final String TOKEN_VAR = "token";

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
	public boolean process(TemplateRenderer renderer, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Language lang = resolveLanguage(request);
        
        String template = (String) request.getAttribute(RENDER_TEMPLATE);
        if (template == null) {
        	String path = request.getServletPath();
        	String prefix = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        	template = prefix.isEmpty() ? "/index" : prefix;

        	LOG.debug("Serving template {} for {}.", template, path);
        }

		// Note: Template is required to start with "/".
		String i18nTemplate = "/" + lang.tag + template;
		
		if (!renderer.application().resourceExists(ContentFilter.TEMPLATES_PATH + i18nTemplate + ContentFilter.TEMPLATE_SUFFIX)) {
			return false;
		}
		
		request.setAttribute("template", i18nTemplate);
		
        resolveDialPrefix(request, lang);
		
		final IServletWebExchange webExchange = renderer.buildExchange(request, response);
        WebContext ctx = new WebContext(webExchange, lang.locale);
        
        fillContext(ctx, request);
		
		/*
		 * Write the response headers
		 */
		response.setContentType("text/html;charset=UTF-8");
		response.setHeader("Pragma", "no-cache");
		response.setHeader("Cache-Control", "no-cache");
		response.setDateHeader("Expires", 0);

		/*
		 * Obtain the response writer
		 */
		final Writer writer = response.getWriter();
		
		renderer.templateEngine().process(i18nTemplate, ctx, writer);
		return true;
	}

	public static Language selectLanguage(Locale locale) {
		return Language.fromLocale(locale);
	}
	
	private static Language resolveLanguage(HttpServletRequest request) {
		Language lang = selectLanguage(request);
        request.setAttribute("currentLang", lang);
        return lang;
	}

	public static Language selectLanguage(HttpServletRequest request) {
		String selectedLang = request.getParameter(LANG_ATTR);
		Language lang;
        if (selectedLang == null) {
        	HttpSession session = request.getSession(false);
        	if (session != null) {
        		lang = (Language) session.getAttribute(LANG_ATTR);
        		if (lang != null) {
        			return lang;
        		}
        	}
        	
    		// Use browser default
    		String acceptLanguageHeader = request.getHeader("Accept-Language");
    		Locale locale;
    		if (acceptLanguageHeader == null) {
				locale = null;
    		} else {
    			List<LanguageRange> acceptLanguage = LanguageRange.parse(acceptLanguageHeader);
    			locale = Locale.lookup(acceptLanguage, Language.supportedLocales());
    		}
    		lang = Language.fromLocale(locale);
    		
    		if (session != null) {
    			// Remember to make next lookup more efficient.
    			session.setAttribute(LANG_ATTR, lang);
    		}
        } else {
        	lang = Language.fromTag(selectedLang);

    		// Remember requested language.
        	request.getSession().setAttribute(LANG_ATTR, lang);
        	
        	// Remember in personal settings.
        	String login = LoginFilter.getAuthenticatedUser(request);
        	if (login != null) {
        		DB db = DBService.getInstance();
        		try (SqlSession tx = db.openSession()) {
        			Users users = tx.getMapper(Users.class);
        			users.setLang(login, lang.tag);
        			tx.commit();
        		}
        	}
        }

        return lang;
	}

	private static void resolveDialPrefix(HttpServletRequest request, Language lang) {
		String dialPrefix = selectDialPrefix(request, lang);
		request.setAttribute("currentDialPrefix", dialPrefix);

		// Also set the international prefix and trunk prefix for the user's country
		List<Country> countries = Countries.countriesFromDialPrefix(dialPrefix);
		String internationalPrefix = "00"; // Default fallback
		String trunkPrefix = "0"; // Default fallback
		if (countries != null && !countries.isEmpty()) {
			Country country = countries.get(0);
			List<String> intPrefixes = country.getInternationalPrefixes();
			if (intPrefixes != null && !intPrefixes.isEmpty()) {
				internationalPrefix = intPrefixes.get(0);
			}
			List<String> trunkPrefixes = country.getTrunkPrefixes();
			if (trunkPrefixes != null && !trunkPrefixes.isEmpty()) {
				trunkPrefix = trunkPrefixes.get(0);
			}
		}
		request.setAttribute("internationalPrefix", internationalPrefix);
		request.setAttribute("trunkPrefix", trunkPrefix);
	}
	
	public static String selectDialPrefix(HttpServletRequest request, Language lang) {
		String selectedPrefix = request.getParameter(DIAL_PREFIX_ATTR);
		String dialPrefix;
		if (selectedPrefix == null) {
			HttpSession session = request.getSession(false);
			if (session != null) {
				String sessionDialPrefix = (String) session.getAttribute(DIAL_PREFIX_ATTR);
				if (sessionDialPrefix != null) {
					return sessionDialPrefix;
				}
			}
			
			// Load from settings.
			String login = LoginFilter.getAuthenticatedUser(request);
			if (login != null) {
				DB db = DBService.getInstance();
				try (SqlSession tx = db.openSession()) {
					Users users = tx.getMapper(Users.class);
					dialPrefix = users.getDialPrefix(login);
				}
			} else {
				// Detect from address.
				Country country = LocationService.getInstance().getCountry(request);
				if (country != null) {
					List<String> dialPrefixes = country.getDialPrefixes();
					dialPrefix = dialPrefixes.size() > 0 ? dialPrefixes.get(0) : lang.dialPrefix;
				} else {
					dialPrefix = lang.dialPrefix;
				}
			}
			
			if (session != null) {
				// Remember to make next lookup more efficient.
				session.setAttribute(DIAL_PREFIX_ATTR, dialPrefix);
			}
		} else {
			dialPrefix = Countries.normalizeDialPrefix(selectedPrefix, lang.dialPrefix);
			
			// Remember requested language.
			request.getSession().setAttribute(DIAL_PREFIX_ATTR, dialPrefix);
			
			// Remember in personal settings.
			String login = LoginFilter.getAuthenticatedUser(request);
			if (login != null) {
				DB db = DBService.getInstance();
				try (SqlSession tx = db.openSession()) {
					Users users = tx.getMapper(Users.class);
					users.setDialPrefix(login, dialPrefix);
					tx.commit();
				}
			}
		}
		
		return dialPrefix;
	}
	
	protected void fillContext(WebContext ctx, HttpServletRequest request) {
		// Use request parameters as template variables (at least as default values).
		for (Enumeration<String> it = request.getParameterNames(); it.hasMoreElements(); ) {
			String attribute = it.nextElement();
			ctx.setVariable(attribute, request.getParameter(attribute));
		}

		// Use request attributes as template variables.
		for (Enumeration<String> it = request.getAttributeNames(); it.hasMoreElements(); ) {
			String attribute = it.nextElement();
			ctx.setVariable(attribute, request.getAttribute(attribute));
		}

		HttpSession session = request.getSession(false);
		if (session != null) {
			Object password = session.getAttribute(PASSWORD_ATTR);
			Object cardDavToken = session.getAttribute(CARD_DAV_TOKEN_ATTR);
			Object apiKey = session.getAttribute(API_KEY_ATTR);

			// Show this only once.
			session.removeAttribute(PASSWORD_ATTR);
			session.removeAttribute(CARD_DAV_TOKEN_ATTR);
			session.removeAttribute(API_KEY_ATTR);

			ctx.setVariable(PASSWORD_ATTR, password);
			ctx.setVariable(CARD_DAV_TOKEN_ATTR, cardDavToken);
			ctx.setVariable(API_KEY_ATTR, apiKey);
		}

		String userName = LoginFilter.getAuthenticatedUser(request);
		if (userName != null) {
			ctx.setVariable("userName", userName);
			ctx.setVariable("supporterId", "PhoneBlock-" + userName.substring(0, 13));
			ctx.setVariable("loggedIn", Boolean.TRUE);
		} else {
			ctx.setVariable("loggedIn", Boolean.FALSE);
		}
		
        ctx.setVariable(LoginServlet.LOCATION_ATTRIBUTE, LoginServlet.location(request, null));
        ctx.setVariable("currentPage", ServletUtil.currentPage(request));
        
        ctx.setVariable("titleKey", "app.defaultTitle");
        ctx.setVariable("descriptionKey", "app.defaultDescription");
        ctx.setVariable("keywords", "");
        
		ctx.setVariable("canonical", "https://phoneblock.net" + request.getContextPath() + request.getServletPath());
        ctx.setVariable("props", PROPS);
        ctx.setVariable("deps", DEPS);
        ctx.setVariable("contextPath", request.getContextPath());
        
    	UserAgentType userAgentType = UserAgentType.detect(request);

    	ctx.setVariable("android", Boolean.valueOf(userAgentType.isAndroid()));
		ctx.setVariable("iphone", Boolean.valueOf(userAgentType.isIPhone()));
		ctx.setVariable("fritzbox", Boolean.valueOf(userAgentType.isFritzBox()));
		ctx.setVariable("inMobileApp", Boolean.valueOf(userAgentType.isMobileApp()));
		ctx.setVariable("userAgentType", userAgentType);
		
		ctx.setVariable("ratings", RATINGS);
		ctx.setVariable("languages", Language.all());
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
