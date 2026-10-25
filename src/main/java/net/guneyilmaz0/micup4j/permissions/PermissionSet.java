package net.guneyilmaz0.micup4j.permissions;

import java.math.BigInteger;
import java.util.Objects;

/** Immutable unsigned 64-bit mask. Unknown bits are preserved when reading. */
public record PermissionSet(BigInteger value) {
    public static final PermissionSet NONE = new PermissionSet(BigInteger.ZERO);
    public static final PermissionSet ALL = new PermissionSet(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE));

    public PermissionSet {
        Objects.requireNonNull(value);
        if (value.signum() < 0 || value.bitLength() > 64)
            throw new IllegalArgumentException("Permissions must be an unsigned 64-bit mask");
    }
    public static PermissionSet parse(String decimal) {
        if (decimal == null || !decimal.matches("[0-9]{1,20}"))
            throw new IllegalArgumentException("Permissions must be decimal digits");
        return new PermissionSet(new BigInteger(decimal));
    }
    public static PermissionSet of(Permission... permissions) {
        BigInteger result = BigInteger.ZERO;
        for (Permission permission : permissions) result = result.setBit(permission.getBit());
        return new PermissionSet(result);
    }
    public boolean contains(Permission permission) { return value.testBit(permission.getBit()); }
    public boolean containsAll(PermissionSet other) { return value.and(other.value).equals(other.value); }
    public PermissionSet union(PermissionSet other) { return new PermissionSet(value.or(other.value)); }
    public PermissionSet overwrite(PermissionSet allow, PermissionSet deny) {
        return new PermissionSet(value.andNot(deny.value).or(allow.value));
    }
    /** Request fields accept at most signed INT64_MAX, although response computations use all 64 bits. */
    public String toRequestValue() {
        if (value.bitLength() > 63) throw new IllegalArgumentException("Permission write exceeds INT64_MAX");
        return toString();
    }
    @Override public String toString() { return value.toString(); }
}
