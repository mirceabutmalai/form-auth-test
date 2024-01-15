/**
 * RADCOM.
 *
 */
package ro.radcom.frm.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * application page type enumeration.
 */
public enum EnumPageType {

    TEST("TEST", "test", "test");

    @SuppressWarnings("PMD.UseConcurrentHashMap")
    private static final Map<String, EnumPageType> VALUE_MAP = new LinkedHashMap<>();
    private final String pageKeyId;
    private final String jspBaseName;
    private final String messageKeyPrefix;

    static {
        for (final EnumPageType aPageType : EnumPageType.values()) {
            VALUE_MAP.put(aPageType.getPageKeyId(), aPageType);
        }
    }

    public static EnumPageType parse(final String pValue) {
        return VALUE_MAP.get(pValue);
    }

    EnumPageType(final String pKey, final String pJsp, final String pMessageKey) {
        pageKeyId = pKey;
        jspBaseName = pJsp;
        messageKeyPrefix = pMessageKey;
    }

    /**
     * @return page tab name
     */
    public final String getPageKeyId() {
        return pageKeyId;
    }

    /**
     * @return jsp base name - usefull to return from spring controller in order to identify jsp page
     */
    public final String getJspBaseName() {
        return jspBaseName;
    }

    /**
     * @return message key prefix for message identification from resource bundle
     */
    public final String getMessageKeyPrefix() {
        return messageKeyPrefix;
    }
}
