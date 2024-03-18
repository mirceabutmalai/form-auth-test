/**
 * RADCOM.
 *
 */
package ro.radcom.frm.servlet;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * add default character set filter.
 */
public final class AddDefaultCharsetFilter
        implements Filter {

    private static final Logger LOG = LogManager.getLogger(AddDefaultCharsetFilter.class);
    private static final String DEFAULT_ENCODING = "ISO-8859-1";
    private static final String CONTENT_TYPE_HEADER_NAME = "Content-Type";
    private static final String CONTENT_TYPE_CHARSET_MATCH = ";charset=";
    private static final int CONTENT_TYPE_CHARSET_MATCH_LENGTH = CONTENT_TYPE_CHARSET_MATCH.length();
    private static final String PARAMETER_INCLUDE_URL_PATTERN = "includeUrlPattern";
    private transient Pattern[] includeUrlPatterns;
    private transient String encoding;

    /**
     * constructor.
     */
    public AddDefaultCharsetFilter() {
        super();
    }

    @Override
    public void init(final FilterConfig pConfig) {

        final Enumeration<String> allNames = pConfig.getInitParameterNames();
        final List<Pattern> aList = new ArrayList<>();
        String calcEncoding = DEFAULT_ENCODING;

        if (allNames != null) {
            while (allNames.hasMoreElements()) {
                final String name = allNames.nextElement();
                final String value = pConfig.getInitParameter(name);

                if ((name == null) || (name.isEmpty())) {
                    continue;
                }

                if (name.startsWith(PARAMETER_INCLUDE_URL_PATTERN)) {
                    final Pattern valuePattern;
                    try {
                        valuePattern = Pattern.compile(value);
                    } catch (Exception ex) {
                        throw ex;
                    }

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("compiled included regex pattern " + value
                                + " at init parameter name " + name);
                    }

                    aList.add(valuePattern);
                } else if (name.equalsIgnoreCase("encoding")) {
                    String initEncoding = value;
                    if ((value == null) || (value.isEmpty()) || (value.equalsIgnoreCase("default"))) {
                        initEncoding = DEFAULT_ENCODING;
                    } else if (value.equalsIgnoreCase("system")) {
                        initEncoding = Charset.defaultCharset().name();
                    } else if (!Charset.isSupported(value)) {
                        throw new IllegalArgumentException("encoding " + value + " is not supported");
                    }
                    calcEncoding = initEncoding;

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("using default encoding " + calcEncoding);
                    }
                }
            }
        }

        //includeUrlPatterns = (aList.isEmpty()) ? null : aList.toArray(Pattern[]::new);
        includeUrlPatterns = (aList.isEmpty()) ? null : aList.toArray(new Pattern[] {});
        encoding = calcEncoding;

        LOG.info("started add default charset filter");
    }

    @Override
    public void destroy() {
        encoding = null;
        includeUrlPatterns = null;

        LOG.info("stopped add default charset filter");
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    public void doFilter(
            final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {

        if (LOG.isTraceEnabled()) {
            LOG.trace("starting filter processing");
        }

        final ServletResponse filterResponse = doBeforeProcessing(request, response);

        Throwable problem = null;
        try {
            chain.doFilter(request, filterResponse);
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

    private ServletResponse doBeforeProcessing(
            final ServletRequest request, final ServletResponse response) {

        if (LOG.isTraceEnabled()) {
            LOG.trace("starting doBeforeProcessing");
        }

        if (request == null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("cannot analyse NULL request object in before processing");
            }
            return response;
        }
        if (!(request instanceof HttpServletRequest)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("ignoring request that is not an instance of HttpServletRequest, protocol="
                        + request.getProtocol() + " in before processing");
            }
            return response;
        }

        final HttpServletRequest httpRequest = (HttpServletRequest) request;
        final boolean bRequestIncluded = checkRequestIncluded(httpRequest);

        if (!bRequestIncluded) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("current request is not included for this filter");
            }
            return response;
        }

        if (!(response instanceof HttpServletResponse)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("ignoring response that is not an instance of HttpServletResponse");
            }
            return response;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("current request is included in filter ==> wrapping response");
        }
        return new InternalResponseWrapper((HttpServletResponse) response, encoding);
    }

    private void doAfterProcessing() {
        if (LOG.isTraceEnabled()) {
            LOG.trace("starting doAfterProcessing");
        }
    }

    private boolean checkRequestIncluded(final HttpServletRequest request) {
        if ((includeUrlPatterns == null) || (includeUrlPatterns.length == 0)) {
            return true;
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
            LOG.trace("checking path " + path + " for inclusion");
        }

        for (final Pattern aPattern : includeUrlPatterns) {
            if (aPattern.matcher(path).matches()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Wrapper that adds a character set for text media types if no character
     * set is specified.
     */
    private static class InternalResponseWrapper
            extends HttpServletResponseWrapper {

        private transient String encoding;

        public InternalResponseWrapper(final HttpServletResponse pResponse, final String pEncoding) {
            super(pResponse);
            encoding = pEncoding;
        }

        @Override
        public void setContentType(final String contentType) {
            if ((contentType == null) || (contentType.isEmpty())) {
                super.setContentType(contentType);
                return;
            }

            String userMimeType;
            String userCharset = null;

            final String lowerContentType = contentType.toLowerCase();
            final int idxOfCharset = lowerContentType.indexOf(CONTENT_TYPE_CHARSET_MATCH);
            if (idxOfCharset < 0) {
                userMimeType = contentType;
            } else {
                userMimeType = contentType.substring(0, idxOfCharset);
                userCharset = contentType.substring(idxOfCharset + CONTENT_TYPE_CHARSET_MATCH_LENGTH);
            }

            if ((userMimeType == null) || (userMimeType.isEmpty())) {
                super.setContentType(contentType);
                return;
            }

            final String trimmedUserMimeType = userMimeType.trim();
            final String trimmedUserCharset = ((userCharset == null) || (userCharset.isEmpty()))
                    ? null : userCharset.trim();
            if ((trimmedUserMimeType == null) || (trimmedUserMimeType.isEmpty())) {
                super.setContentType(contentType);
                return;
            }
            
            if ((trimmedUserCharset != null) && (!trimmedUserCharset.isEmpty())) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("user specified charset in response: content-type=" + contentType);
                }

                super.setContentType(contentType);
                encoding = getCharacterEncoding();
                return;
            }

            if ((trimmedUserMimeType.startsWith("text/"))
                    || (trimmedUserMimeType.equalsIgnoreCase("application/javascript"))
                    || (trimmedUserMimeType.equalsIgnoreCase("application/ecmascript"))
                    || (trimmedUserMimeType.equalsIgnoreCase("application/json"))
                    || (trimmedUserMimeType.equalsIgnoreCase("application/jsonml+json"))
                    || (trimmedUserMimeType.equalsIgnoreCase("application/ccxml+xml"))
                    || (trimmedUserMimeType.equalsIgnoreCase("application/wsdl+xml"))
                    || (trimmedUserMimeType.equalsIgnoreCase("application/xhtml+xml"))
                    || (trimmedUserMimeType.equalsIgnoreCase("application/xml"))
                    || (trimmedUserMimeType.equalsIgnoreCase("application/xslt+xml"))
                    || (trimmedUserMimeType.equalsIgnoreCase("image/svg+xml"))) {

                final String aValue = trimmedUserMimeType + CONTENT_TYPE_CHARSET_MATCH + encoding;
                if (LOG.isTraceEnabled()) {
                    LOG.trace("adding encoding to matched mime type: new content-type=" + aValue);
                }

                super.setContentType(aValue);
                return;
            }

            super.setContentType(contentType);
        }

        @Override
        public void setCharacterEncoding(final String charset) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("user presented charset as " + charset);
            }
            super.setCharacterEncoding(charset);
            encoding = charset;
        }

        @Override
        public void setHeader(final String name, final String value) {
            if ((name == null) || (name.isEmpty())) {
                super.setHeader(name, value);
                return;
            }

            final String trimmedName = name.trim();
            if (!trimmedName.equalsIgnoreCase(CONTENT_TYPE_HEADER_NAME)) {
                super.setHeader(name, value);
                return;
            }

            setContentType(value);
        }

        @Override
        public void addHeader(final String name, final String value) {
            if ((name == null) || (name.isEmpty())) {
                super.addHeader(name, value);
                return;
            }

            final String trimmedName = name.trim();
            if (!trimmedName.equalsIgnoreCase(CONTENT_TYPE_HEADER_NAME)) {
                super.addHeader(name, value);
                return;
            }

            setContentType(value);
        }
    }
}
