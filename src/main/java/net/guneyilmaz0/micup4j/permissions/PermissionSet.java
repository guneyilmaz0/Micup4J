package net.guneyilmaz0.micup4j.permissions;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Immutable unsigned 64-bit mask. Unknown bits are preserved when reading.
 *
 * @param value the mask, 0 to 2^64-1
 */
public record PermissionSet(BigInteger value) {
    /** The empty mask. */
    public static final PermissionSet NONE = new PermissionSet(BigInteger.ZERO);
    /** Every bit set, as computed for owners and administrators. */
    public static final PermissionSet ALL = new PermissionSet(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE));

    /**
     * Validates the mask.
     *
     * @throws IllegalArgumentException if the value is negative or wider than 64 bits
     */
    public PermissionSet {
        Objects.requireNonNull(value);
        if (value.signum() < 0 || value.bitLength() > 64)
            throw new IllegalArgumentException("Permissions must be an unsigned 64-bit mask");
    }
    /**
     * Parses a decimal mask as Micup sends it.
     *
     * @param decimal 1-20 decimal digits
     * @return the mask
     * @throws IllegalArgumentException if the text is not a decimal mask
     */
    public static PermissionSet parse(String decimal) {
        if (decimal == null || !decimal.matches("[0-9]{1,20}"))
            throw new IllegalArgumentException("Permissions must be decimal digits");
        return new PermissionSet(new BigInteger(decimal));
    }
    /**
     * Creates a mask from permissions.
     *
     * @param permissions the permissions
     * @return the mask
     */
    public static PermissionSet of(Permission... permissions) {
        BigInteger result = BigInteger.ZERO;
        for (Permission permission : permissions) result = result.setBit(permission.getBit());
        return new PermissionSet(result);
    }
    /**
     * Returns whether one bit is set.
     *
     * @param permission the permission
     * @return {@code true} if set
     */
    public boolean contains(Permission permission) { return value.testBit(permission.getBit()); }
    /**
     * Returns whether every bit of another mask is set.
     *
     * @param other the required bits
     * @return {@code true} if all are set
     */
    public boolean containsAll(PermissionSet other) { return value.and(other.value).equals(other.value); }
    /**
     * Returns the union of two masks.
     *
     * @param other the other mask
     * @return the union
     */
    public PermissionSet union(PermissionSet other) { return new PermissionSet(value.or(other.value)); }
    /**
     * Applies an overwrite: clears the denied bits, then sets the allowed bits.
     *
     * @param allow the bits to set
     * @param deny  the bits to clear
     * @return the result
     */
    public PermissionSet overwrite(PermissionSet allow, PermissionSet deny) {
        return new PermissionSet(value.andNot(deny.value).or(allow.value));
    }
    /**
     * Returns the decimal string for request fields, which accept at most signed {@code INT64_MAX}, although response
     * computations use all 64 bits.
     *
     * @return the decimal mask
     * @throws IllegalArgumentException if bit 63 is set
     */
    public String toRequestValue() {
        if (value.bitLength() > 63) throw new IllegalArgumentException("Permission write exceeds INT64_MAX");
        return toString();
    }
    @Override public String toString() { return value.toString(); }
}
