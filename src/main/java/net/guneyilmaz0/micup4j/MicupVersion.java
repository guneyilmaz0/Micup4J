package net.guneyilmaz0.micup4j;

import java.io.IOException;
import java.util.Properties;

/** Build metadata shared by discovery, REST requests and applications. */
public final class MicupVersion {
    private static final String VERSION = loadVersion();

    private MicupVersion() {}

    /**
     * Returns the library version from the build.
     *
     * @return the version, such as {@code 1.0.0}
     */
    public static String getVersion() { return VERSION; }
    /**
     * Returns the HTTP {@code User-Agent} sent with every request.
     *
     * @return {@code Micup4J/<version>}
     */
    public static String getUserAgent() { return "Micup4J/" + VERSION; }

    private static String loadVersion() {
        try (var input = MicupVersion.class.getResourceAsStream("/micup4j.properties")) {
            if (input == null) return "unknown";
            var properties = new Properties();
            properties.load(input);
            return properties.getProperty("version", "unknown");
        } catch (IOException error) {
            throw new ExceptionInInitializerError("Cannot read Micup4J build metadata");
        }
    }
}
