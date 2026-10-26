package net.guneyilmaz0.micup4j.requests.restaction;

import net.guneyilmaz0.micup4j.requests.RestAction;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A guild mutation that records an optional audit log reason, modelled on JDA's {@code AuditableRestAction}.
 *
 * <p>Micup stores the reason verbatim and never percent-decodes it. Header reasons must therefore be printable ASCII;
 * Micup4J rejects other characters instead of sending text the server would store garbled. Ban and timeout actions send
 * the reason in the JSON body instead, which accepts any Unicode text. Values longer than 512 characters after trimming
 * are rejected locally; Micup itself would silently drop them.
 *
 * @param <T> the result type
 */
public class AuditableRestAction<T> extends RestAction<T> {
    /** The request header Micup reads the audit log reason from. */
    public static final String REASON_HEADER = "X-Audit-Log-Reason";
    /** The longest accepted reason, in characters after trimming. */
    public static final int MAX_REASON_LENGTH = 512;

    private final State<T> state;

    /**
     * Creates an action whose request is built on every execution from the reason current at that time.
     *
     * @param factory      builds the request from the trimmed reason, or {@code null} when none is set
     * @param headerReason {@code true} when the reason travels in {@link #REASON_HEADER} and must be printable ASCII
     */
    public AuditableRestAction(Function<String, RestAction<T>> factory, boolean headerReason) {
        this(new State<>(factory, headerReason));
    }

    private AuditableRestAction(State<T> state) {
        super(() -> state.factory.apply(state.reason).submit());
        this.state = state;
    }

    /**
     * Sets the audit log reason for this action.
     *
     * @param reason the reason; {@code null}, empty or blank clears it
     * @return this action
     * @throws IllegalArgumentException if the trimmed reason exceeds 512 characters, or contains characters a header
     *                                  cannot carry for a header-based action
     */
    public AuditableRestAction<T> reason(String reason) {
        state.reason = checkReason(reason, state.headerReason);
        return this;
    }

    /**
     * Returns the reason that the next execution sends.
     *
     * @return the trimmed reason, or {@code null}
     */
    public String getReason() { return state.reason; }

    /**
     * Returns the headers for a header-based reason.
     *
     * @param reason the checked reason, or {@code null}
     * @return an empty map, or the reason header
     */
    public static Map<String, String> reasonHeaders(String reason) {
        return reason == null ? Map.of() : Map.of(REASON_HEADER, reason);
    }

    static String checkReason(String reason, boolean header) {
        if (reason == null || reason.isBlank()) return null;
        String trimmed = reason.strip();
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_REASON_LENGTH)
            throw new IllegalArgumentException("Audit log reason exceeds " + MAX_REASON_LENGTH + " characters");
        if (header && !trimmed.chars().allMatch(c -> c == '\t' || (c >= 0x20 && c < 0x7F)))
            throw new IllegalArgumentException("Audit log reason headers must be printable ASCII");
        return trimmed;
    }

    private static final class State<T> {
        final Function<String, RestAction<T>> factory;
        final boolean headerReason;
        volatile String reason;
        State(Function<String, RestAction<T>> factory, boolean headerReason) {
            this.factory = Objects.requireNonNull(factory); this.headerReason = headerReason;
        }
    }
}
