package dev.technix.mica.internal;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Mica's own version, read from {@code mica-version.properties} (filled in by Gradle). The
 * slim library jar has no {@code fabric.mod.json}, so Fabric's mod metadata cannot be used.
 */
public final class MicaVersion {

    private static final String VERSION = read();

    private MicaVersion() {
    }

    @NotNull
    public static String get() {
        return VERSION;
    }

    private static String read() {
        try (InputStream stream = MicaVersion.class.getClassLoader()
                .getResourceAsStream("mica-version.properties")) {
            if (stream == null) {
                return "unknown";
            }
            Properties properties = new Properties();
            properties.load(stream);
            return properties.getProperty("version", "unknown");
        } catch (IOException exception) {
            return "unknown";
        }
    }
}
