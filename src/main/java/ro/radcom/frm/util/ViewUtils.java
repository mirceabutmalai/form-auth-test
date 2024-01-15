/**
 * RADCOM.
 *
 */
package ro.radcom.frm.util;

import javax.servlet.jsp.PageContext;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ro.radcom.frm.model.EnumPageType;
import ro.radcom.frm.model.EnumRightType;
import ro.radcom.frm.model.EnumRoleType;

/**
 * jspx view utils.
 */
@SuppressWarnings("PMD.UseUtilityClass")
public final class ViewUtils {

    private static final Logger LOG = LogManager.getLogger(ViewUtils.class);
    private static final Pattern RESOURCE_URL_PATTERN = Pattern.compile("(.*)(/res(\\d){2,2}/)(.*)");
    private static final String RESOURCE_REAL_FOLDER = "/res08/";

    /**
     * constructor.
     */
    private ViewUtils() {
        super();
    }

    public static String makeUrl(final PageContext pageCtx, final String path) {
        final HttpServletRequest httpReq = (HttpServletRequest) pageCtx.getRequest();
        final HttpServletResponse httpResp = (HttpServletResponse) pageCtx.getResponse();

        return makeUrl(httpReq, httpResp, path);
    }

    @SuppressWarnings({"PMD.NPathComplexity", "PMD.AvoidLiteralsInIfCondition"})
    public static String makeUrl(
            final HttpServletRequest httpReq, final HttpServletResponse httpResp, final String path) {

        final String ctxPath = httpReq.getContextPath();

        final StringBuilder sb = new StringBuilder();
        if ((ctxPath != null) && (!(ctxPath.isEmpty()))) {
            sb.append(ctxPath);
        }

        if ((path == null) || (path.isEmpty())) {
            return httpResp.encodeURL(sb.toString());
        }

        if (path.charAt(0) == '/') {
            sb.append(path);
            final String finalUrl = sb.toString();

            final Matcher aMatcher = RESOURCE_URL_PATTERN.matcher(finalUrl);
            if (aMatcher.matches()) {
                // aceste tipuri de URL nu au nevoie de encodare jsessionid
                return aMatcher.group(1) + RESOURCE_REAL_FOLDER + aMatcher.group(4);
            }
            return httpResp.encodeURL(finalUrl);
        }

        String aRequestUri = (String) httpReq.getAttribute("javax.servlet.forward.request_uri");
        if ((aRequestUri == null) || (aRequestUri.isEmpty())) {
            aRequestUri = httpReq.getRequestURI();
        }
        if ((aRequestUri == null) || (aRequestUri.isEmpty())) {
            sb.append('/').append(path);
            final String finalUrl = sb.toString();

            final Matcher aMatcher = RESOURCE_URL_PATTERN.matcher(finalUrl);
            if (aMatcher.matches()) {
                // aceste tipuri de URL nu au nevoie de encodare jsessionid
                return aMatcher.group(1) + RESOURCE_REAL_FOLDER + aMatcher.group(4);
            }
            return httpResp.encodeURL(finalUrl);
        }
        if ((ctxPath != null) && (!ctxPath.isEmpty()) && (!aRequestUri.startsWith(ctxPath))) {
            return httpResp.encodeURL(sb.toString());
        }

        if ((ctxPath != null) && (!ctxPath.isEmpty())) {
            aRequestUri = aRequestUri.substring(ctxPath.length());
        }
        if ((aRequestUri == null) || (aRequestUri.isEmpty())) {
            sb.append('/').append(path);
            final String finalUrl = sb.toString();

            final Matcher aMatcher = RESOURCE_URL_PATTERN.matcher(finalUrl);
            if (aMatcher.matches()) {
                // aceste tipuri de URL nu au nevoie de encodare jsessionid
                return aMatcher.group(1) + RESOURCE_REAL_FOLDER + aMatcher.group(4);
            }
            return httpResp.encodeURL(finalUrl);
        }

        if (aRequestUri.charAt(0) != '/') {
            sb.append('/').append(path);
            final String finalUrl = sb.toString();

            final Matcher aMatcher = RESOURCE_URL_PATTERN.matcher(finalUrl);
            if (aMatcher.matches()) {
                // aceste tipuri de URL nu au nevoie de encodare jsessionid
                return aMatcher.group(1) + RESOURCE_REAL_FOLDER + aMatcher.group(4);
            }
            return httpResp.encodeURL(finalUrl);
        }

        final int lastSlashIdx = aRequestUri.lastIndexOf('/');
        aRequestUri = aRequestUri.substring(0, lastSlashIdx);

        if ((aRequestUri == null) || (aRequestUri.isEmpty())) {
            sb.append('/').append(path);
            final String finalUrl = sb.toString();

            final Matcher aMatcher = RESOURCE_URL_PATTERN.matcher(finalUrl);
            if (aMatcher.matches()) {
                // aceste tipuri de URL nu au nevoie de encodare jsessionid
                return aMatcher.group(1) + RESOURCE_REAL_FOLDER + aMatcher.group(4);
            }
            return httpResp.encodeURL(finalUrl);
        }

        sb.append(aRequestUri);
        sb.append('/').append(path);
        final String finalUrl = sb.toString();

        final Matcher aMatcher = RESOURCE_URL_PATTERN.matcher(finalUrl);
        if (aMatcher.matches()) {
            // aceste tipuri de URL nu au nevoie de encodare jsessionid
            return aMatcher.group(1) + RESOURCE_REAL_FOLDER + aMatcher.group(4);
        }
        return httpResp.encodeURL(finalUrl);
    }

    public static boolean hasAccessOnPage(
            final HttpServletRequest request, final String page, final String right) {

        final EnumPageType pageType = EnumPageType.parse(page);
        final EnumRightType rightType = EnumRightType.parse(right);

        return EnumRoleType.hasAccessOnPage(request, pageType, rightType);
    }

    public static boolean isUserInRole(
            final HttpServletRequest request, final String role) {

        return request.isUserInRole(role);
    }

    public static String formatDate(final PageContext page, final Date date, final String format) {
        if (date == null) {
            return null;
        }
        if ((format == null) || (format.isEmpty())) {
            return null;
        }

        final SimpleDateFormat sdf = new SimpleDateFormat(format, calcLocale(page));
        return sdf.format(date);
    }

    public static String formatMinuteOfDay(final Integer minOfDay, final String format) {
        if (minOfDay == null) {
            return null;
        }
        if ((format == null) || (format.isEmpty())) {
            return null;
        }

        // FIXME: aceasta functie este dubioasa. Ar fi bine sa folosim Calendar
        final SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
        final long tstamp = minOfDay * 60_000L - 7_200_000L;
        final Date date = new Date(tstamp);
        final String rval = sdf.format(date);

        if (LOG.isTraceEnabled()) {
            LOG.trace("formatting minOfDay " + minOfDay + ", tstamp " + tstamp
                    + " with format " + format + " ==> " + rval);
        }
        return rval;
    }

    public static String formatCurrentDate(final PageContext page, final String format) {
        if ((format == null) || (format.isEmpty())) {
            return null;
        }

        final Date now = new Date();
        final SimpleDateFormat sdf = new SimpleDateFormat(format, calcLocale(page));
        return sdf.format(now);
    }

    public static String escapeForLikeOperator(final String value) {
        if ((value == null) || (value.isEmpty())) {
            return value;
        }

        String rval = value;
        rval = rval.replace("\\", "\\\\");
        rval = rval.replace("%", "\\%");
        rval = rval.replace("_", "\\_");
        return rval;
    }

    public static String calcLanguage(final PageContext page) {
        return calcLocale(page).getLanguage();
    }

    private static Locale calcLocale(final PageContext page) {
        return Locale.ENGLISH;
    }
}
