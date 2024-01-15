/**
 * RADCOM.
 *
 */
package ro.radcom.frm.util;

import java.util.Collection;
import java.util.Map;

/**
 * logging utility methods.
 */
public final class WarLogUtil {

    private WarLogUtil() {
        super();
    }

    @SuppressWarnings({"PMD.NPathComplexity"})
    public static String printMap(final Map aMap, final boolean withNewLines, final int pLevel) {
        final StringBuilder sb = new StringBuilder(512);

        if ((aMap == null) || (aMap.isEmpty())) {
            sb.append("<EMPTY>");
            return sb.toString();
        }

        sb.append(aMap.getClass().getSimpleName()).append('{');
        if (withNewLines) {
            sb.append('\n');
        }

        boolean bFirst = true;
        for (final Object aObjEntry : aMap.entrySet()) {
            if (bFirst) {
                bFirst = false;
            } else {
                if (withNewLines) {
                    sb.append('\n');
                    for (int i = 0; i < pLevel; i++) {
                        sb.append('\t');
                    }
                } else {
                    sb.append(", ");
                }
            }

            if (aObjEntry == null) {
                sb.append("<NULL>");
            }
            if (!(aObjEntry instanceof Map.Entry)) {
                continue;
            }

            final Map.Entry aEntry = (Map.Entry) aObjEntry;
            final Object aKey = aEntry.getKey();
            final Object aValue = aEntry.getValue();

            sb.append('{');
            sb.append(aKey);
            sb.append(", ");

            if (aValue instanceof Collection) {
                sb.append(printCollection((Collection) aValue, withNewLines, pLevel + 1));
            } else if (aValue instanceof Map) {
                sb.append(printMap((Map) aValue, withNewLines, pLevel + 1));
            } else {
                sb.append(aValue);
            }
            sb.append('}');
        }

        if (withNewLines) {
            sb.append('\n');
            for (int i = 0; i < pLevel - 1; i++) {
                sb.append('\t');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    @SuppressWarnings({"PMD.NPathComplexity"})
    public static String printCollection(
            final Collection aList, final boolean withNewLines, final int pLevel) {

        final StringBuilder sb = new StringBuilder(512);

        if ((aList == null) || (aList.isEmpty())) {
            sb.append("<EMPTY>");
            return sb.toString();
        }

        sb.append(aList.getClass().getSimpleName()).append('{');
        if (withNewLines) {
            sb.append('\n');
        }

        boolean aInternalFirst = true;
        for (final Object aValue : aList) {
            if (aInternalFirst) {
                aInternalFirst = false;
            } else {
                if (withNewLines) {
                    sb.append('\n');
                    for (int i = 0; i < pLevel; i++) {
                        sb.append('\t');
                    }
                } else {
                    sb.append(", ");
                }
            }

            if (aValue instanceof Collection) {
                sb.append(printCollection((Collection) aValue, withNewLines, pLevel + 1));
            } else if (aValue instanceof Map) {
                sb.append(printMap((Map) aValue, withNewLines, pLevel + 1));
            } else {
                sb.append(aValue);
            }
        }

        if (withNewLines) {
            sb.append('\n');
            for (int i = 0; i < pLevel - 1; i++) {
                sb.append('\t');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    public static String printRawData(final byte[] rawData) {
        final StringBuilder sb = new StringBuilder(512);

        if (rawData == null) {
            sb.append("<NULL>");
            return sb.toString();
        }
        if (rawData.length == 0) {
            sb.append("[array.length=<ZERO>]");
            return sb.toString();
        }

        sb.append('[');
        int idx = 0;
        for (final byte oneValue : rawData) {
            final int firstNibble = (((int) oneValue) >> 4) & 0x0F;
            final int secondNibble = ((int) oneValue) & 0x0F;
            if ((idx % 4 == 0) && (idx > 0)) {
                sb.append(' ');
            }
            printOneNibble(sb, firstNibble);
            printOneNibble(sb, secondNibble);
            idx++;
        }
        sb.append(']');
        return sb.toString();
    }

    @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
    private static void printOneNibble(final StringBuilder sb, final int nibble) {
        if (nibble < 0x0A) {
            sb.append(nibble);
            return;
        }
        final char hexChar = (char) (((int) 'A') + (nibble - 0x0A));
        sb.append(hexChar);
    }
}
