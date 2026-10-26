package net.guneyilmaz0.micup4j.entities;

/** Snowflakes retain all unsigned 64 bits; a Java long can be negative. */
public interface ISnowflake {
    String getId();
    default long getIdLong() { return Long.parseUnsignedLong(getId()); }
}
