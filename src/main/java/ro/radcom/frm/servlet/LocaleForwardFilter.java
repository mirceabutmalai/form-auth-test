/**
 * RADCOM.
 *
 */
package ro.radcom.frm.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * locale filter.
 */
public final class LocaleForwardFilter
        implements Filter {

    private static final Logger LOG = LogManager.getLogger(LocaleForwardFilter.class);
    private static final String PARAMETER_EXCLUDE_URL_PATTERN = "excludeUrlPattern";
    private transient Pattern[] excludeUrlPatterns;
    private transient ServletContext servletContext;

    /**
     * constructor.
     */
    public LocaleForwardFilter() {
        super();
    }

    @Override
    public void init(final FilterConfig pConfig) {

        final ServletContext ctx = pConfig.getServletContext();

        final Enumeration<String> allNames = pConfig.getInitParameterNames();
        final List<Pattern> aList = new ArrayList<>();

        if (allNames != null) {
            while (allNames.hasMoreElements()) {
                final String name = allNames.nextElement();
                final String value = pConfig.getInitParameter(name);

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

        servletContext = ctx;
        excludeUrlPatterns = (aList.isEmpty()) ? null : aList.toArray(Pattern[]::new);

        LOG.info("started locale filter");
    }

    @Override
    public void destroy() {
        excludeUrlPatterns = null;
        servletContext = null;

        LOG.info("stopped locale filter");
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
            if (problem instanceof ServletException servletProblem) {
                throw servletProblem;
            }
            if (problem instanceof IOException ioProblem) {
                throw ioProblem;
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
            LOG.warn("cannot analyse NULL request object");
            return;
        }
        if (!(request instanceof HttpServletRequest)) {
            LOG.warn("ignoring request that is not an instance of HttpServletRequest, protocol="
                    + request.getProtocol());
            return;
        }

        final HttpServletRequest httpRequest = (HttpServletRequest) request;

        if (checkRequestExcluded(httpRequest)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("current request is excluded from locale filter");
            }
            return;
        }

        final MainAppServlet.LocaleData requestCookieLocale
                = MainAppServlet.findCookieLocale(httpRequest);
        if (requestCookieLocale != null) {
            if (response == null) {
                LOG.warn("cannot analyse NULL response object in before processing");
                return;
            }
            if (!(response instanceof HttpServletResponse)) {
                LOG.warn("ignoring response that is not an instance of HttpServletResponse in before processing");
                return;
            }

            MainAppServlet.initializeLocale(
                    httpRequest, (HttpServletResponse) response,
                    requestCookieLocale.locale, requestCookieLocale.localeValue);
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
}
