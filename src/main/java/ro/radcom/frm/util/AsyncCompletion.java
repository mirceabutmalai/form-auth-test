/**
 * RADCOM.
 *
 */
package ro.radcom.frm.util;

/**
 * async completion interface.
 * <p>
 * This interface is a notification interface for a completion event. It is used to notify about
 * task completion events when they occur.</p>
 * <p/>
 * @param <M> type of completion object delivered
 */
public interface AsyncCompletion<M extends Object> {

    /**
     * called when async process is completed.
     * <p>
     * either rval parameter or ex is passed through onComplete procedure in order to signal success
     * or failure of completion process.</p>
     * <p/>
     * @param rval completion success return value
     */
    void onComplete(M rval);
}
