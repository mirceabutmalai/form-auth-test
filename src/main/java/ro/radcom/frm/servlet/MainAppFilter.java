/**
 * RADCOM.
 *
 */
package ro.radcom.frm.servlet;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.catalina.TomcatPrincipal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ietf.jgss.GSSCredential;
import ro.radcom.frm.model.EnumRoleType;
import ro.radcom.frm.model.WarUserInfo;

/**
 * main application filter.
 */
public final class MainAppFilter
        implements Filter {

    private static final Logger LOG = LogManager.getLogger(MainAppFilter.class);
    private static final String PARAMETER_EXCLUDE_URL_PATTERN = "excludeUrlPattern";
    private transient Pattern[] excludeUrlPatterns;
    private final transient AtomicInteger userInfoIdGenerator = new AtomicInteger(1);

    /**
     * constructor.
     */
    public MainAppFilter() {
        super();
    }

    @Override
    public void init(final FilterConfig pConfig) {

        final Enumeration<String> allNames = pConfig.getInitParameterNames();
        final List<Pattern> aList = new ArrayList<>();

        if (allNames != null) {
            while (allNames.hasMoreElements()) {
                final String name = allNames.nextElement();
                final String value = pConfig.getInitParameter(name);

                if ((name == null) || (name.isEmpty())) {
                    continue;
                }

                if (name.startsWith(PARAMETER_EXCLUDE_URL_PATTERN)) {
                    final Pattern valuePattern;
                    try {
                        valuePattern = Pattern.compile(value);
                    } catch (Exception ex) {
                        throw ex;
                    }

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("compiled excluded regex pattern " + value
                                + " at init parameter name " + name);
                    }

                    aList.add(valuePattern);
                }
            }
        }

        excludeUrlPatterns = (aList.isEmpty()) ? null : aList.toArray(Pattern[]::new);

        LOG.info("started main application filter");
    }

    @Override
    public void destroy() {
        excludeUrlPatterns = null;

        LOG.info("stopped main application filter");
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    public void doFilter(
            final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {

        if (LOG.isTraceEnabled()) {
            LOG.trace("starting filter processing");
        }

        doBeforeProcessing(request, response);

        Throwable problem = null;
        try {
            chain.doFilter(request, response);
        } catch (Throwable t) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("problem executing doFilter", t);
            }

            // If an exception is thrown somewhere down the filter chain, we still want to execute
            // our after processing, and then rethrow the problem after that.
            problem = t;
        }

        doAfterProcessing();

        // If there was a problem, we want to rethrow it if it is a known type, otherwise log it.
        if (problem != null) {
            if (problem instanceof ServletException) {
                throw (ServletException) problem;
            }
            if (problem instanceof IOException) {
                throw (IOException) problem;
            }
            throw new ServletException(problem);
        }
    }

    @SuppressWarnings({"PMD.NPathComplexity", "PMD.AvoidDeeplyNestedIfStmts"})
    private void doBeforeProcessing(final ServletRequest request, final ServletResponse response) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("starting doBeforeProcessing");
        }

        if (request == null) {
            LOG.warn("cannot analyse NULL request object in before processing");
            return;
        }
        if (!(request instanceof HttpServletRequest)) {
            LOG.warn("ignoring request that is not an instance of HttpServletRequest, protocol="
                    + request.getProtocol() + " in before processing");
            return;
        }

        final HttpServletRequest httpRequest = (HttpServletRequest) request;
        final boolean bRequestExcluded = checkRequestExcluded(httpRequest);

        if (!bRequestExcluded) {
            final MainAppServlet.LocaleData requestCookieLocale
                    = MainAppServlet.findCookieLocale(httpRequest);

            if (response == null) {
                LOG.warn("cannot analyse NULL response object in before processing");
                return;
            }
            if (!(response instanceof HttpServletResponse)) {
                LOG.warn("ignoring response that is not an instance of HttpServletResponse in before processing");
                return;
            }

            final HttpServletResponse httpResponse = (HttpServletResponse) response;

            if (requestCookieLocale != null) {
                MainAppServlet.initializeLocale(
                        httpRequest, httpResponse,
                        requestCookieLocale.locale, requestCookieLocale.localeValue);
            }

            MainAppServlet.checkRefreshCookieLocale(httpRequest, httpResponse);
        }

        final HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("session is not yet created for this browser request flow");
            }
            return;
        }

        final String requestRemoteUser = httpRequest.getRemoteUser();
        final Principal requestUserPrincipal = httpRequest.getUserPrincipal();
        final boolean requestUserIsAuth = (requestUserPrincipal != null);

        if ((!requestUserIsAuth) || (requestRemoteUser == null) || (requestRemoteUser.isEmpty())) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("current request access does not have a user authenticated");
            }
            return;
        }

        if (bRequestExcluded) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("current request is excluded from main app filter");
            }
            return;
        }

        final Set<EnumRoleType> requestUserRoles = makeRequestUserRolesSet(httpRequest);

        synchronized (session) {
            final WarUserInfo userInfo = extractUserInfo(session);
            if (userInfo == null) {
                LOG.info("detected user login event for username " + requestRemoteUser);
                doUserLogin(session, null, requestRemoteUser, requestUserRoles, requestUserPrincipal);
                return;
            }

            final String sessionUsername = userInfo.getContainerUsername();
            final Set<EnumRoleType> sessionUserRoles = userInfo.getSetOfWebappRoles();

            if ((!requestRemoteUser.equals(sessionUsername))
                    || (((!requestUserRoles.isEmpty())
                    || ((sessionUserRoles != null) && (!sessionUserRoles.isEmpty())))
                    && (!requestUserRoles.equals(sessionUserRoles)))) {

                LOG.info("detected user re-authentication in previous auth session for username " + requestRemoteUser
                        + " ==> this may be done by container after a management event on this context");
                clearSessionData(session);
                doUserLogin(session, userInfo, requestRemoteUser, requestUserRoles, requestUserPrincipal);
            }
        }
    }

    private void doAfterProcessing() {
        if (LOG.isTraceEnabled()) {
            LOG.trace("starting doAfterProcessing");
        }
    }

    private boolean checkRequestExcluded(final HttpServletRequest request) {
        if ((excludeUrlPatterns == null) || (excludeUrlPatterns.length == 0)) {
            return false;
        }

        final String servletPath = request.getServletPath();
        final String pathInfo = request.getPathInfo();

        final StringBuilder sb = new StringBuilder();
        if ((servletPath != null) && (!servletPath.isEmpty())) {
            sb.append(servletPath);
        }
        if ((pathInfo != null) && (!pathInfo.isEmpty())) {
            sb.append(pathInfo);
        }

        final String path = sb.toString();

        if ((path == null) || (path.isEmpty())) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("no path to check for current request");
            }
            return false;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("checking path " + path + " for exclusion");
        }

        for (final Pattern aPattern : excludeUrlPatterns) {
            if (aPattern.matcher(path).matches()) {
                return true;
            }
        }

        return false;
    }

    private Set<EnumRoleType> makeRequestUserRolesSet(final HttpServletRequest request) {
        final Set<EnumRoleType> rval = EnumSet.noneOf(EnumRoleType.class);
        for (final EnumRoleType aRole : EnumRoleType.values()) {
            if (request.isUserInRole(aRole.getRoleName())) {
                rval.add(aRole);
            }
        }
        return rval;
    }

    private WarUserInfo extractUserInfo(final HttpSession session) {
        final Object objUserInfo = session.getAttribute(WarUserInfo.ATTRIBUTE_NAME);
        WarUserInfo userInfo = null;
        if ((objUserInfo != null) && (objUserInfo instanceof WarUserInfo aUserInfo)) {
            userInfo = aUserInfo;
        }
        return userInfo;
    }

    private void doUserLogin(
            final HttpSession session, final WarUserInfo oldUserInfo, final String requestUsername,
            final Set<EnumRoleType> requestUserRoles, final Principal userPrinc) {

        final WarUserInfo userInfo;
        if ((oldUserInfo != null) && (requestUsername.equals(oldUserInfo.getContainerUsername()))) {
            userInfo = initUserInfo(oldUserInfo, requestUsername, requestUserRoles);
        } else {
            userInfo = initUserInfo(null, requestUsername, requestUserRoles);
        }

        GSSCredential tmpGssCredential = null;
        if ((userPrinc != null) && (userPrinc instanceof TomcatPrincipal tomcatPrincipal)) {
            tmpGssCredential = tomcatPrincipal.getGssCredential();
        }

        userInfo.setValidLdapResult(false);
        userInfo.setDisplayName(null);
        userInfo.setHasGssCredential(false);

        final Set<EnumRoleType> userInfoWebRoles = userInfo.getSetOfWebappRoles();
        final Collection<String> webRoles = new ArrayList<>();
        if ((userInfoWebRoles != null) && (!userInfoWebRoles.isEmpty())) {
            for (final EnumRoleType oneRole : userInfoWebRoles) {
                webRoles.add(oneRole.getRoleName());
            }
        }

        if (oldUserInfo == null) {
            LOG.info("new user login: userInfo=" + userInfo);
        } else if (requestUsername.equals(oldUserInfo.getContainerUsername())) {
            LOG.info("user reauthenticated on same http session: userInfo=" + userInfo);
        } else {
            LOG.info("closing authenticated user for " + oldUserInfo.getContainerUsername()
                    + " with id " + oldUserInfo.getId()
                    + " and starting new user login on same http session: userInfo=" + userInfo);
        }

        session.setAttribute(WarUserInfo.ATTRIBUTE_NAME, userInfo);
    }

    private void clearSessionData(HttpSession session) {
        session.removeAttribute(WarUserInfo.ATTRIBUTE_NAME);
    }

    private WarUserInfo initUserInfo(
            final WarUserInfo oldUserInfo, final String requestUsername,
            final Set<EnumRoleType> requestUserRoles) {

        final WarUserInfo userInfo = new WarUserInfo();
        final long now = System.currentTimeMillis();
        
        final long authStartTime;
        final String identifier;

        if (oldUserInfo == null) {
            authStartTime = now;

            final StringBuilder sb = new StringBuilder();
            sb.append(userInfoIdGenerator.getAndIncrement());
            sb.append('-');
            sb.append(now);
            identifier = sb.toString();
        } else {
            authStartTime = oldUserInfo.getAuthStartTstamp();
            identifier = oldUserInfo.getId();
        }

        // aceste campuri provin mereu din containerul servlet in care rulam
        userInfo.setId(identifier);
        userInfo.setContainerUsername(requestUsername);
        userInfo.setSetOfWebappRoles(requestUserRoles);
        userInfo.setAuthStartTstamp(authStartTime);
        userInfo.setLastRefreshTstamp(now);

        return userInfo;
    }
}
