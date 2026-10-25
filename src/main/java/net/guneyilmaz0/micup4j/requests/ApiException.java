package net.guneyilmaz0.micup4j.requests;

/** Contains only status and API error code; response bodies and credential-bearing URLs are omitted. */
public final class ApiException extends RuntimeException {
    private final int status;
    private final String code;

    public ApiException(int status, String code) {
        super("Micup HTTP " + status + " (" + code + ")");
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
