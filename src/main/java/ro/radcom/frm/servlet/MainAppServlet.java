/**
 * RADCOM.
 *
 */
package ro.radcom.frm.servlet;

import java.util.Enumeration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.jstl.core.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ro.radcom.frm.util.ViewUtils;

/**
 * main application servlet.
 */
public final class MainAppServlet
        extends HttpServlet {

    private static final Logger LOG = LogManager.getLogger(MainAppServlet.class);
    private static final String PATH_LOGOUT = "/logout";
    private static final String PATH_STATUS = "/status";
    private static final String PATH_CHANGE_LOCALE = "/changeLocale";
    private static final String PARAM_NAME_LANG = "lang";
    private static final Pattern LANG_VALUE_PATTERN = Pattern.compile("^(en|ro)$");
    private static final String ATTRNAME_SESSION_LANGUAGE_VALUE = "x-form-auth-langValue";
    private static final String ATTRNAME_SESSION_LANGUAGE_REFRESH = "x-form-auth-langRefresh";
    private static final String COOKIE_NAME_LOCALE = "x-form-auth-cookie-locale";
    private static final int COOKIE_LOCALE_MAXAGE = 30 * 24 * 60 * 60; // one month
    private static final long COOKIE_LOCALE_REFRESH_PERIOD = 10L * 60L * 1000L; // 10 minutes

    /**
     * constructor.
     */
    public MainAppServlet() {
        super();
    }

    @Override
    public void init()
            throws ServletException {

        super.init();

        LOG.info("initialized main app servlet");
    }

    @Override
    public void destroy() {
        super.destroy();

        LOG.info("destroyed main app servlet");
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) {
        processRequest(request, response);
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) {
        processRequest(request, response);
    }

    private void processRequest(
            final HttpServletRequest request, final HttpServletResponse response) {

        //response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "no-cache");

        final String pathInfo = request.getPathInfo();
        if ((pathInfo != null) && (pathInfo.equals(PATH_LOGOUT))) {
            processLogout(request, response);
            return;
        } else if ((pathInfo != null) && (pathInfo.equals(PATH_STATUS))) {
            processStatus(request, response);
            return;
        } else if ((pathInfo != null) && (pathInfo.equals(PATH_CHANGE_LOCALE))) {
            processChangeLocale(request, response);
            return;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("unknown path info " + pathInfo);
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    private void processLogout(
            final HttpServletRequest request, final HttpServletResponse response) {

        final HttpSession session = request.getSession(false);

        final String username = request.getRemoteUser();
        if ((username == null) || (username.isEmpty())) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("logout for no authenticated user so we just invalidate session");
            }

            if (session != null) {
                session.invalidate();
            }

            try {
                response.sendRedirect(ViewUtils.makeUrl(request, response, "/logout.jspx"));
            } catch (Exception ex) {
                LOG.error("failed to send redirect response in logout", ex);
            }
            return;
        }

        LOG.info("logout authenticated user " + username + " ==> we just invalidate session");

        if (session != null) {
            session.invalidate();
        }

        try {
            response.sendRedirect(ViewUtils.makeUrl(request, response, "/logout.jspx"));
        } catch (Exception ex) {
            LOG.error("failed to send redirect response in logout", ex);
        }
    }

    private void processStatus(
            final HttpServletRequest request, final HttpServletResponse response) {

        if (LOG.isTraceEnabled()) {
            LOG.trace("checking status");
        }

        // TODO: ar trebui sa oferim un raspuns in functie de status callControlManager
        response.setStatus(200);
    }

    private void processChangeLocale(
            final HttpServletRequest request, final HttpServletResponse response) {

        final Enumeration<String> paramNames = request.getParameterNames();
        if (paramNames == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("no parameter names available in change locale");
            }

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String langParamValue = null;

        while (paramNames.hasMoreElements()) {
            final String aParamName = paramNames.nextElement();
            if ((aParamName == null) || (aParamName.isEmpty())) {
                continue;
            }

            final String trimmedParamName = aParamName.trim();
            if ((trimmedParamName == null) || (trimmedParamName.isEmpty())) {
                continue;
            }

            if (trimmedParamName.equalsIgnoreCase(PARAM_NAME_LANG)) {
                langParamValue = request.getParameter(aParamName);
            }
        }
        
        if ((langParamValue == null) || (langParamValue.isEmpty())) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("lang parameter value is <EMPTY>");
            }

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        final Matcher matcher = LANG_VALUE_PATTERN.matcher(langParamValue);
        if (!matcher.matches()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("lang parameter value is invalid (does not matches pattern)");
            }

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        final Locale locale;
        try {
            locale = new Locale(langParamValue);
        } catch (Exception ex) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("invalid language param value received: " + langParamValue, ex);
            }

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (locale == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("null Locale for language param value " + langParamValue);
            }

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        final HttpSession session = request.getSession(true);
        changeLocale(request, response, session, locale, langParamValue, true);

        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    public static LocaleData findCookieLocale(final HttpServletRequest request) {
        final Cookie[] requestCookies = request.getCookies();
        if ((requestCookies == null) || (requestCookies.length == 0)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("no request cookies");
            }
            return null;
        }

        String localeCookieValue = null;
        for (final Cookie aCookie : requestCookies) {
            if (aCookie == null) {
                continue;
            }

            final String aCookiePath = aCookie.getPath();
            if (LOG.isTraceEnabled()) {
                LOG.trace("checking cookie with path " + aCookiePath
                        + " and name " + aCookie.getName());
            }

            if ((aCookie.getName() == null) || (aCookie.getName().isEmpty())) {
                continue;
            }
            if (aCookie.getName().equals(MainAppServlet.COOKIE_NAME_LOCALE)) {
                localeCookieValue = aCookie.getValue();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("found cookie locale with value " + localeCookieValue);
                }
                break;
            }
        }

        if ((localeCookieValue == null) || (localeCookieValue.isEmpty())) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("no value for locale cookie");
            }
            return null;
        }

        final Matcher matcher = LANG_VALUE_PATTERN.matcher(localeCookieValue);
        if (!matcher.matches()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("locale cookie value is invalid (does not matches pattern)");
            }
            return null;
        }

        final Locale locale;
        try {
            locale = new Locale(localeCookieValue);
        } catch (Exception ex) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("invalid language locale cookie value received: " + localeCookieValue, ex);
            }
            return null;
        }

        if (locale == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("null Locale for language locale cookie value value " + localeCookieValue);
            }
            return null;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("found valid locale cookie with value " + localeCookieValue);
        }

        return new LocaleData(locale, localeCookieValue);
    }

    public static void initializeLocale(
            final HttpServletRequest request, final HttpServletResponse response,
            final Locale cookieLocale, final String cookieValue) {

        if (cookieLocale == null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("nothing to initialize since NULL cookie locale");
            }
            return;
        }

        final HttpSession existentSession = request.getSession(false);
        if (existentSession != null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("we have session ==> checking locale on session");
            }

            final Object jstlLocale = Config.get(existentSession, Config.FMT_LOCALE);
            if (jstlLocale == null) {
                changeLocale(request, response, existentSession, cookieLocale, cookieValue, false);
                return;
            }
            if (!(jstlLocale instanceof Locale)) {
                changeLocale(request, response, existentSession, cookieLocale, cookieValue, false);
                return;
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("we already detected jstl locale on session ==> not initializing");
            }
            return;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("creating session in initialize locale");
        }

        final HttpSession newSession = request.getSession(true);
        changeLocale(request, response, newSession, cookieLocale, cookieValue, false);
    }

    public static void checkRefreshCookieLocale(
            final HttpServletRequest request, final HttpServletResponse response) {
        
        final HttpSession session = request.getSession(false);
        if (session == null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("no session in check refresh cookie locale");
            }
            return;
        }

        final Object objLangValue = session.getAttribute(ATTRNAME_SESSION_LANGUAGE_VALUE);
        if ((objLangValue == null) || (!(objLangValue instanceof String))) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("no lang value session attribute in check refresh cookie locale");
            }
            return;
        }

        final String langValue = (String) objLangValue;
        if (langValue.isEmpty()) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("lang value session attribute is EMPTY in check refresh cookie locale");
            }
            return;
        }

        final boolean isSecure = determineSecureForCookie(request);
        final Object objLastRefresh = session.getAttribute(ATTRNAME_SESSION_LANGUAGE_REFRESH);
        if ((objLastRefresh == null) || (!(objLastRefresh instanceof Long))) {
            setupLangCookie(response, session, request.getServletContext(), langValue, isSecure);
            return;
        }

        final long lastRefresh = (Long) objLastRefresh;
        final long now = System.currentTimeMillis();
        if (now - lastRefresh > COOKIE_LOCALE_REFRESH_PERIOD) {
            setupLangCookie(response, session, request.getServletContext(), langValue, isSecure);
            return;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("no need to refresh language cookie");
        }
    }

    private static void changeLocale(
            final HttpServletRequest request, final HttpServletResponse response,
            final HttpSession session, final Locale locale, final String langValue,
            final boolean bWithSetCookie) {

        changeLocaleForJstl(session, locale);

        session.setAttribute(ATTRNAME_SESSION_LANGUAGE_VALUE, langValue);

        if (!bWithSetCookie) {
            session.setAttribute(ATTRNAME_SESSION_LANGUAGE_REFRESH, System.currentTimeMillis());
            return;
        }

        final boolean isSecure = determineSecureForCookie(request);
        setupLangCookie(response, session, request.getServletContext(), langValue, isSecure);
    }

    private static void changeLocaleForJstl(final HttpSession session, final Locale locale) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("changing locale to " + locale.getDisplayName() + " for jstl view");
        }
        Config.set(session, Config.FMT_LOCALE, locale);
    }

    private static boolean determineSecureForCookie(final HttpServletRequest request) {
        // TODO: daca cererea vine din GSLB ar trebui sa ne uitam la niste headere ca sa ne dam
        //       seama ca cererea originala este secure
        return request.isSecure();
    }

    private static void setupLangCookie(
            final HttpServletResponse response, final HttpSession session,
            final ServletContext srvCtx, final String langValue, final boolean isSecure) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("setup language cookie");
        }

        final long now = System.currentTimeMillis();
        response.addCookie(makeLangCookie(srvCtx, langValue, isSecure));
        session.setAttribute(ATTRNAME_SESSION_LANGUAGE_REFRESH, now);
    }

    private static Cookie makeLangCookie(
            final ServletContext srvCtx, final String langValue, final boolean isSecure) {

        final Cookie langCookie = new Cookie(COOKIE_NAME_LOCALE, langValue);
        langCookie.setPath(srvCtx.getContextPath());
        langCookie.setMaxAge(COOKIE_LOCALE_MAXAGE);
        langCookie.setHttpOnly(true);
        langCookie.setSecure(isSecure);
        return langCookie;
    }

    public static final class LocaleData {
        public final Locale locale;
        public final String localeValue;

        public LocaleData(final Locale pLocale, final String pLocaleValue) {
            super();
            locale = pLocale;
            localeValue = pLocaleValue;
        }
    }
}
