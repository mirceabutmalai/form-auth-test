/**
 * RADCOM.
 *
 */
package ro.radcom.frm.util;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * set response header tag handler.
 */
public final class SetResponseStatusHandler
        extends TagSupport {

    private static final Logger LOG = LogManager.getLogger(SetResponseStatusHandler.class);
    private Integer value;

    //<editor-fold defaultstate="collapsed" desc="GETTERS and SETTERS">
    /**
     * @param pValue value to set on status
     */
    public void setValue(final Integer pValue) {
        value = pValue;
    }
    //</editor-fold>

    /**
     * constructor.
     */
    public SetResponseStatusHandler() {
        super();
    }

    @Override
    public int doStartTag()
            throws JspException {

        final HttpServletResponse resp;
        try {
            resp = (HttpServletResponse) pageContext.getResponse();
        } catch (Exception ex) {
            LOG.warn("failed to get http servlet response object", ex);
            return SKIP_BODY;
        }

        if (resp == null) {
            LOG.warn("http servlet response object is null");
            return SKIP_BODY;
        }

        if (value == null) {
            LOG.warn("no value to set status on http servlet response");
            return SKIP_BODY;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("setting http servlet response status to " + value);
        }

        resp.setStatus(value);
        return SKIP_BODY;
    }
}
