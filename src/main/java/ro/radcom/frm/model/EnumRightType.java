/**
 * RADCOM.
 *
 */
package ro.radcom.frm.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * right type for use by application.
 */
public enum EnumRightType {

    NONE(0, "NONE"),
    READ(1, "READ"),
    WRITE(2, "WRITE");

    @SuppressWarnings("PMD.UseConcurrentHashMap")
    private static final Map<String, EnumRightType> VALUE_MAP = new LinkedHashMap<>();
    private final int level;
    private final String rightName;

    static {
        for (final EnumRightType aRightType : EnumRightType.values()) {
            VALUE_MAP.put(aRightType.getRightName(), aRightType);
        }
    }

    public static EnumRightType parse(final String pValue) {
        final EnumRightType rval = VALUE_MAP.get(pValue);
        if (rval == null) {
            return NONE;
        }

        return rval;
    }

    EnumRightType(final int pLevel, final String pName) {
        level = pLevel;
        rightName = pName;
    }

    public final int getIntLevel() {
        return level;
    }

    public final String getRightName() {
        return rightName;
    }
}
