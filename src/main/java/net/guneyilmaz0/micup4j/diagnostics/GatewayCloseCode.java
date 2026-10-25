package net.guneyilmaz0.micup4j.diagnostics;

import java.util.Arrays;

/** Policy for the documented main-Gateway close codes. Unknown/transport codes retry with Resume. */
public enum GatewayCloseCode {
    UNKNOWN_ERROR(4000, Action.RESUME), UNKNOWN_OPCODE(4001, Action.STOP), DECODE_ERROR(4002, Action.STOP),
    NOT_AUTHENTICATED(4003, Action.IDENTIFY), AUTHENTICATION_FAILED(4004, Action.STOP),
    ALREADY_AUTHENTICATED(4005, Action.STOP), INVALID_SEQUENCE(4007, Action.IDENTIFY),
    RATE_LIMITED(4008, Action.RESUME), SESSION_TIMEOUT(4009, Action.RESUME), INVALID_SHARD(4010, Action.STOP),
    SHARDING_REQUIRED(4011, Action.STOP), INVALID_VERSION(4012, Action.STOP), TRANSPORT(-1, Action.RESUME);
    public enum Action { RESUME, IDENTIFY, STOP }
    private final int code;
    private final Action action;
    GatewayCloseCode(int code, Action action) { this.code = code; this.action = action; }
    public int getCode() { return code; }
    public Action getAction() { return action; }
    public static GatewayCloseCode fromCode(int code) { return Arrays.stream(values()).filter(value -> value.code == code).findFirst().orElse(TRANSPORT); }
}
