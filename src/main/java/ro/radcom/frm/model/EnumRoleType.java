/**
 * RADCOM.
 *
 */
package ro.radcom.frm.model;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * application role type enumaration.
 */
public enum EnumRoleType {

    FORM_AUTH_ADMIN("FORM-AUTH-ADMIN"),
    FORM_AUTH_OPER("FORM-AUTH-OPER");

    @SuppressWarnings("PMD.UseConcurrentHashMap")
    private static final Map<String, EnumRoleType> VALUE_MAP = new LinkedHashMap<>();
    private static final Logger LOG = LogManager.getLogger(EnumRoleType.class);
    private final String roleName;
    private final Map<EnumPageType, EnumRightType> mapOfRightsPerPage;

    static {
        for (final EnumRoleType aRoleType : EnumRoleType.values()) {
            aRoleType.initRights();
            VALUE_MAP.put(aRoleType.getRoleName(), aRoleType);
        }
    }

    public static EnumRoleType parse(final String pValue) {
        return VALUE_MAP.get(pValue);
    }

    @SuppressWarnings("PMD.NPathComplexity")
    public static EnumRightType findRightType(
            final HttpServletRequest request, final EnumPageType page) {

        if ((request == null) || (page == null)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("invalid parameters in findRightType request=" + request
                        + ", page=" + page);
            }
            return EnumRightType.NONE;
        }

        EnumRightType currentRight = EnumRightType.NONE;
        if (request.isUserInRole(FORM_AUTH_ADMIN.getRoleName())) {
            final EnumRightType aRight = FORM_AUTH_ADMIN.checkRightType(page);
            if (currentRight.getIntLevel() < aRight.getIntLevel()) {
                currentRight = aRight;
            }
        }
        if (request.isUserInRole(FORM_AUTH_OPER.getRoleName())) {
            final EnumRightType aRight = FORM_AUTH_OPER.checkRightType(page);
            if (currentRight.getIntLevel() < aRight.getIntLevel()) {
                currentRight = aRight;
            }
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("find right type on page " + page + ": currentRight is " + currentRight);
        }
        return currentRight;
    }

    public static boolean hasAccessOnPage(
            final HttpServletRequest request, final EnumPageType page, final EnumRightType right) {

        if ((request == null) || (page == null)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("invalid parameters request=" + request + ", page=" + page);
            }
            return false;
        }

        final EnumRightType requestedRight = (right == null) ? EnumRightType.READ : right;
        final EnumRightType currentRight = findRightType(request, page);

        if (LOG.isTraceEnabled()) {
            LOG.trace("checking access on page " + page + ": currentRight is " + currentRight
                    + " and requested right is " + requestedRight);
        }

        return (currentRight.getIntLevel() >= requestedRight.getIntLevel());
    }

    EnumRoleType(final String pValue) {
        roleName = pValue;
        mapOfRightsPerPage = new EnumMap<>(EnumPageType.class);
    }

    private void initRights() {
        switch (this) {
            case FORM_AUTH_ADMIN -> {
                mapOfRightsPerPage.put(EnumPageType.TEST, EnumRightType.WRITE);
            }

            case FORM_AUTH_OPER -> {
                mapOfRightsPerPage.put(EnumPageType.TEST, EnumRightType.WRITE);
            }

            default -> {}
        }
    }

    public final String getRoleName() {
        return roleName;
    }

    public final EnumRightType checkRightType(final EnumPageType pageType) {
        final EnumRightType rval = mapOfRightsPerPage.get(pageType);
        if (rval != null) {
            return rval;
        }

        return EnumRightType.NONE;
    }
}
