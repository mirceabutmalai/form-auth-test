/**
 * RADCOM.
 * 
 */

package ro.radcom.frm;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ro.radcom.frm.model.WarUserInfo;

/**
 * HttpSessionListenerImpl.
 */
public final class HttpSessionListenerImpl
        implements HttpSessionListener {

    private static final Logger LOG = LogManager.getLogger(HttpSessionListenerImpl.class);

    /**
     * constructor.
     */
    public HttpSessionListenerImpl() {
        super();
    }

    @Override
    public void sessionCreated(final HttpSessionEvent se) {
        HttpSessionListener.super.sessionCreated(se);
        se.getSession().getId();
        se.getSession().getMaxInactiveInterval();
    }

    @Override
    public void sessionDestroyed(final HttpSessionEvent se) {
        HttpSessionListener.super.sessionDestroyed(se);

        se.getSession().getId();
        se.getSession().getMaxInactiveInterval();

        final HttpSession session = se.getSession();
        if (session == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("session destroy called with null session");
            }
            return;
        }

        final WarUserInfo userInfo = (WarUserInfo) session.getAttribute(WarUserInfo.ATTRIBUTE_NAME);
        if (userInfo == null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("ignoring session destroy for non authenticated user session");
            }
            return;
        }

        final long lastAccessTime = session.getLastAccessedTime();
        final long now = System.currentTimeMillis();

        final long inactivityTime = now - lastAccessTime;
        final int configuredInactiveInterval = session.getMaxInactiveInterval();

        boolean destroyByTimeout = false;
        if ((configuredInactiveInterval > 0)
                && (((int) (inactivityTime / 1000L)) >= configuredInactiveInterval)) {
            destroyByTimeout = true;
        }

        final long creationTime = session.getCreationTime();
        final long authTime = userInfo.getAuthStartTstamp();

        final long totalDuration = now - creationTime;
        final long authDuration = now - authTime;
        final long authActiveDuration = lastAccessTime - authTime;

        LOG.info("session ended for id " + userInfo.getId()
                + " and username " + userInfo.getContainerUsername()
                + ": totalDuration=" + totalDuration + ", authDuration=" + authDuration
                + ", authActiveDuration=" + authActiveDuration
                + ", destroyByTimeout=" + destroyByTimeout);
    }
}
