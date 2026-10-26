package net.guneyilmaz0.micup4j.requests;

/** Contains only status and API error code; response bodies and credential-bearing URLs are omitted. */
public final class ApiException extends RuntimeException {
    /** The HTTP status code. */
    private final int status;
    /** Micup's error code, sanitized to a safe identifier. */
    private final String code;

    /**
     * Creates the exception.
     *
     * @param status the HTTP status
     * @param code   Micup's error code
     */
    public ApiException(int status, String code) {
        super("Micup HTTP " + status + " (" + code + ")");
        this.status = status;
        this.code = code;
    }

    /**
     * Returns the HTTP status code.
     *
     * @return the HTTP status code
     */
    public int getStatus() {
        return status;
    }

    /**
     * Returns Micup's error code, such as {@code MISSING_PERMISSIONS}, or {@code UNKNOWN}.
     *
     * @return Micup's error code, such as {@code MISSING_PERMISSIONS}, or {@code UNKNOWN}
     */
    public String getCode() {
        return code;
    }
}
