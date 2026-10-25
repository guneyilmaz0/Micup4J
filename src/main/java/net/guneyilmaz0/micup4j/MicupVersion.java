package net.guneyilmaz0.micup4j;

import java.io.IOException;
import java.util.Properties;

/** Build metadata shared by discovery, REST requests and applications. */
public final class MicupVersion {
    private static final String VERSION = loadVersion();

    private MicupVersion() {}

    public static String getVersion() { return VERSION; }
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
