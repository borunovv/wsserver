package com.borunovv.util;

/**
 * @author borunovv
 */
public class DebugUtils {

    public static String toString(byte[] data, int fromIndex ,int toIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = fromIndex; i < toIndex; ++i) {
            if (i > fromIndex) {
                sb.append(',');
            }
            sb.append(data[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
