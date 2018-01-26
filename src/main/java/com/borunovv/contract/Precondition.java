package com.borunovv.contract;

public final class Precondition {

    public static void expected(boolean condition, String description) {
        if (! condition) {
            throw new PreconditionException(description);
        }
    }

    public static void notNull(Object obj, String description) {
        if (obj == null) {
            throw new PreconditionException(description);
        }
    }
}
