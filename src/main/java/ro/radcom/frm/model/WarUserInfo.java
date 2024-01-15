/**
 * RADCOM.
 *
 */
package ro.radcom.frm.model;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import ro.radcom.frm.util.WarLogUtil;

/**
 * user informations for use in view.
 */
public final class WarUserInfo
        implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final String ATTRIBUTE_NAME = "x-user-info";
    // aceste campuri sunt extrase exclusiv din ldap service
    private String id;
    private String displayName;
    private boolean hasGssCredential;
    private boolean validLdapResult;
    // aceste campuri sunt determinate de authentificarea servlet container
    private String containerUsername;
    private Set<EnumRoleType> setOfWebappRoles;
    private long authStartTstamp;
    private long lastRefreshTstamp;

    //<editor-fold defaultstate="collapsed" desc="GETTERS and SETTERS">
    /**
     * @return user information identifier (unique per session)
     */
    public String getId() {
        return id;
    }

    /**
     * @param pValue user information identifier (unique per session)
     */
    public void setId(final String pValue) {
        id = pValue;
    }

    /**
     * @return display name
     */
    public String getDisplayName() {
        if (!validLdapResult) {
            return containerUsername;
        }

        return displayName;
    }

    /**
     * @param pValue display name
     */
    public void setDisplayName(final String pValue) {
        displayName = pValue;
    }

    /**
     * @return if the current user has delegated gss credential (this is a sign that the current
     *         user has authenticated via windows integrated authentication)
     */
    public boolean isHasGssCredential() {
        if (!validLdapResult) {
            return false;
        }
        return hasGssCredential;
    }

    /**
     * @param pValue if the current user has delegated gss credential
     */
    public void setHasGssCredential(final boolean pValue) {
        hasGssCredential = pValue;
    }

    /**
     * @return if ldap field are valid and ok to be evaluated
     */
    public boolean isValidLdapResult() {
        return validLdapResult;
    }

    /**
     * @param pValue if ldap field are valid and ok to be evaluated
     */
    public void setValidLdapResult(final boolean pValue) {
        validLdapResult = pValue;
    }

    /**
     * @return servlet container username (this is the user that was authenticated in windows if
     * windows integrated auth was used or the username that was entered in our login form)
     */
    public String getContainerUsername() {
        return containerUsername;
    }

    /**
     * @param pValue servlet container username
     */
    public void setContainerUsername(final String pValue) {
        containerUsername = pValue;
    }

    /**
     * @return roles from servlet container == this set only contains roles that were declared in
     *         web.xml. Other security groups from active directory are not visible here
     */
    public Set<EnumRoleType> getSetOfWebappRoles() {
        if ((setOfWebappRoles == null) || (setOfWebappRoles.isEmpty()))
        {
            return null;
        }
        return Collections.unmodifiableSet(setOfWebappRoles);
    }

    /**
     * @param pValue roles from servlet container
     */
    public void setSetOfWebappRoles(final Set<EnumRoleType> pValue) {
        if ((pValue == null) || (pValue.isEmpty())) {
            setOfWebappRoles = null;
        } else {
            setOfWebappRoles = EnumSet.copyOf(pValue);
        }
    }

    /**
     * @return authenticated start for this bean
     */
    public long getAuthStartTstamp() {
        return authStartTstamp;
    }

    /**
     * @param pValue authenticated start for this bean
     */
    public void setAuthStartTstamp(final long pValue) {
        authStartTstamp = pValue;
    }

    /**
     * @return last data refresh for this bean
     */
    public long getLastRefreshTstamp() {
        return lastRefreshTstamp;
    }

    /**
     * @param pValue last data refresh for this bean
     */
    public void setLastRefreshTstamp(final long pValue) {
        lastRefreshTstamp = pValue;
    }
    //</editor-fold>

    /**
     * constructor.
     */
    public WarUserInfo() {
        super();
    }

    @Override
    public String toString() {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS", Locale.US);

        final StringBuilder sb = new StringBuilder(512);
        sb.append(WarUserInfo.class.getSimpleName()).append('{');
        sb.append("id=").append(id);
        sb.append(", displayName=").append(displayName);
        sb.append(", hasGssCredential=").append(hasGssCredential);
        sb.append(", validLdapResult=").append(validLdapResult);

        // this last fields will always be present
        sb.append(", containerUsername=").append(containerUsername);
        sb.append(", setOfWebappRoles=").append(WarLogUtil.printCollection(setOfWebappRoles, false, 0));
        sb.append(", authStartTstamp=").append(sdf.format(new Date(authStartTstamp)));
        sb.append(", lastRefreshTstamp=").append(sdf.format(new Date(lastRefreshTstamp)));
        sb.append('}');
        return sb.toString();
    }
}
