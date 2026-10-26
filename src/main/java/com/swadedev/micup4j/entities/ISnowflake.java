package com.swadedev.micup4j.entities;

/** Snowflakes retain all unsigned 64 bits; a Java long can be negative. */
public interface ISnowflake {
    /**
     * Returns the id as an unsigned decimal string.
     *
     * @return the id as an unsigned decimal string
     */
    String getId();
    /**
     * Returns the id as a long; values above {@code Long.MAX_VALUE} are negative.
     *
     * @return the id as a long; values above {@code Long.MAX_VALUE} are negative
     */
    default long getIdLong() { return Long.parseUnsignedLong(getId()); }
}
