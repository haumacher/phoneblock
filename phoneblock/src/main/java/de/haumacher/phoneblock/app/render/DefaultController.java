package de.haumacher.phoneblock.app.render;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
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
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.servlet.IServletWebExchange;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.LoginServlet;
import de.haumacher.phoneblock.app.RegistrationServlet;
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
		Language language = Language.fromLocale(locale);
		if (language != null) {
			return language;
		}
		
		Language fallback = Language.fromLocale(new Locale(locale.getLanguage()));
		if (fallback != null) {
			return fallback;
		}
		
		return Language.getDefault();
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
    		if (locale == null) {
    			lang = Language.getDefault();
    		} else {
    			lang = Language.fromLocale(locale);
    		}
    		
    		if (session != null) {
    			// Remember to make next lookup more efficient.
    			session.setAttribute(LANG_ATTR, lang);
    		}
        } else {
        	lang = selectLanguage(selectedLang);

    		// Remember requested language.
        	request.getSession().setAttribute(LANG_ATTR, lang);
        	
        	// Remember in personal settings.
        	String login = LoginFilter.getAuthenticatedUser(request);
        	if (login != null) {
        		DB db = DBService.getInstance();
        		try (SqlSession tx = db.openSession()) {
        			Users users = tx.getMapper(Users.class);
        			users.setLocale(login, lang.tag);
        			tx.commit();
        		}
        	}
        }

        return lang;
	}

	private static void resolveDialPrefix(HttpServletRequest request, Language lang) {
		String dialPrefix = selectDialPrefix(request, lang);
		request.setAttribute("currentDialPrefix", dialPrefix);
	}
	
	public static String selectDialPrefix(HttpServletRequest request, Language lang) {
		String selectedPrefix = request.getParameter(DIAL_PREFIX_ATTR);
		String dialPrefix = null;
		if (selectedPrefix == null) {
			HttpSession session = request.getSession(false);
			if (session != null) {
				dialPrefix = (String) session.getAttribute(DIAL_PREFIX_ATTR);
				if (dialPrefix != null) {
					return dialPrefix;
				}
			}
			
			// Detect from address.
			Country country = LocationService.getInstance().getCountry(request);
			if (country != null) {
				List<String> dialPrefixes = country.getDialPrefixes();
				dialPrefix = dialPrefixes.size() > 0 ? dialPrefixes.get(0) : null;
			}
			
			if (session != null) {
				// Remember to make next lookup more efficient.
				session.setAttribute(DIAL_PREFIX_ATTR, dialPrefix);
			}
		} else {
			dialPrefix = Countries.selectDialPrefix(selectedPrefix);
			
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
		
		if (dialPrefix == null) {
			dialPrefix = lang.dialPrefix;
		}
		
		return dialPrefix;
	}
	
	public static Language selectLanguage(String selectedLang) {
		// Normalize value.
		Language language = Language.fromTag(selectedLang);
		if (language == null) {
			return Language.getDefault();
		} else {
			return language;
		}
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
