package com.theFox6.knWrapper;

public enum KNResponse {
    @SuppressWarnings("unused")
    OVERRIDE(0b0001),
    @SuppressWarnings("unused")
    UPDATE(0b0110),
    UPDATED(0b0100),
    @SuppressWarnings("unused")
    SHUTDOWN(0b0001),
    @SuppressWarnings("unused")
    RESTART(0b0100),
    WRAPPER_ERROR(0b1000),
    /**
     * KleinerNerd state could not be found
     */
    EMPTY(0b0001),
    /**
     * KleinerNerd state could not be determined/classified
     */
    UNKNOWN(0b1000);
    private static final int start      = 0b0001;
    private static final int update     = 0b0010;
    private static final int restart    = 0b0100;
    private static final int unexpected = 0b1000;
    private final int flags;

    KNResponse(int code) {
        this.flags = code;
    }

    private boolean hasFlags(int code) {
        return (flags & code) == code;
    }

    public boolean allowsStart() {
        return hasFlags(start);
    }

    public boolean shouldRestart() {
        return hasFlags(restart);
    }

    public boolean shouldUpdate() {
        return hasFlags(update);
    }

    public boolean isUnexpected() {
        return hasFlags(unexpected);
    }
}
