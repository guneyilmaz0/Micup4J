package net.guneyilmaz0.micup4j.diagnostics;

import java.util.Arrays;

/** Policy for the documented main-Gateway close codes. Unknown and transport codes retry with Resume. */
public enum GatewayCloseCode {
    /** 4000: an unexpected server error; resume. */
    UNKNOWN_ERROR(4000, Action.RESUME),
    /** 4001: the client sent an unknown opcode; stop. */
    UNKNOWN_OPCODE(4001, Action.STOP),
    /** 4002: a payload could not be decoded; stop. */
    DECODE_ERROR(4002, Action.STOP),
    /** 4003: a command was sent before authentication; identify again. */
    NOT_AUTHENTICATED(4003, Action.IDENTIFY),
    /** 4004: the token was rejected; stop without reconnecting. */
    AUTHENTICATION_FAILED(4004, Action.STOP),
    /** 4005: Identify was sent twice; stop. */
    ALREADY_AUTHENTICATED(4005, Action.STOP),
    /** 4007: the resume sequence was invalid; identify a new session. */
    INVALID_SEQUENCE(4007, Action.IDENTIFY),
    /** 4008: a connection or session limit was reached; resume after backoff. */
    RATE_LIMITED(4008, Action.RESUME),
    /** 4009: the session timed out; resume. */
    SESSION_TIMEOUT(4009, Action.RESUME),
    /** 4010: the shard pair is malformed; stop. */
    INVALID_SHARD(4010, Action.STOP),
    /** 4011: the shard owns more than 2,500 guilds; stop and shard further. */
    SHARDING_REQUIRED(4011, Action.STOP),
    /** 4012: the Gateway version is not supported; stop. */
    INVALID_VERSION(4012, Action.STOP),
    /** Any other close or a lost transport; resume. */
    TRANSPORT(-1, Action.RESUME);

    /** What the client does after a close. */
    public enum Action {
        /** Reconnect and resume the session. */
        RESUME,
        /** Reconnect and identify a new session. */
        IDENTIFY,
        /** Fail permanently. */
        STOP
    }

    private final int code;
    private final Action action;
    GatewayCloseCode(int code, Action action) { this.code = code; this.action = action; }

    /**
     * Returns the WebSocket close code.
     *
     * @return the code, or -1 for {@link #TRANSPORT}
     */
    public int getCode() { return code; }

    /**
     * Returns the client's reaction to this close.
     *
     * @return the action
     */
    public Action getAction() { return action; }

    /**
     * Resolves a close code.
     *
     * @param code the WebSocket close code
     * @return the policy entry, or {@link #TRANSPORT} for an undocumented code
     */
    public static GatewayCloseCode fromCode(int code) { return Arrays.stream(values()).filter(value -> value.code == code).findFirst().orElse(TRANSPORT); }
}
