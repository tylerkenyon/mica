package dev.technix.mica.internal;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


public final class FontLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("mica");

    private static final Map<Identifier, byte[]> CACHE = new HashMap<>();

    private FontLoader() {
    }

    @Nullable
    public static byte[] read(@Nullable Identifier id) {
        if (id == null) {
            return null;
        }
        byte[] cached = CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        byte[] fromManager = readFromResourceManager(id);
        if (fromManager == null) {
            fromManager = readFromVanishProvider(id);
        }
        if (fromManager == null) {
            fromManager = readFromClassloader(id);
        }
        if (fromManager == null) {
            fromManager = readFromDisk(id);
        }
        if (fromManager == null) {
            LOGGER.warn("Font resource missing: {}", id);
            return null;
        }
        CACHE.put(id, fromManager);
        return fromManager;
    }

    @Nullable
    private static byte[] readFromDisk(@NotNull Identifier id) {
        String fileName = id.getPath();
        if (fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        }
        return readFromFontsDir(fileName);
    }

    /**
     * Reads {@code fileName} from {@code <gamedir>/mica/fonts}, or {@code null} when it is not there.
     *
     * <p>Package-private rather than folded into {@link #readFromDisk} because the default faces are
     * not {@code Identifier}-keyed: {@code ImGuiFonts} overrides them by fixed file name, and
     * duplicating the game-directory resolution there would be the second place it could go wrong.
     *
     * <p>Resolved against Minecraft's game directory rather than the working directory, which differ
     * in a dev launch.
     */
    @Nullable
    static byte[] readFromFontsDir(@NotNull String fileName) {
        try {
            java.nio.file.Path diskPath =
                    gameDirectory().resolve("mica").resolve("fonts").resolve(fileName);
            if (java.nio.file.Files.exists(diskPath)) {
                return java.nio.file.Files.readAllBytes(diskPath);
            }
        } catch (java.io.IOException | RuntimeException exception) {
            LOGGER.warn("Could not read disk font {}", fileName, exception);
        }
        return null;
    }

    public static java.util.List<String> listExternalFonts() {
        java.util.List<String> list = new java.util.ArrayList<>();
        try {
            java.nio.file.Path dir = gameDirectory().resolve("mica").resolve("fonts");
            if (java.nio.file.Files.exists(dir)) {
                try (var stream = java.nio.file.Files.list(dir)) {
                    stream.filter(p -> p.toString().endsWith(".ttf") || p.toString().endsWith(".otf"))
                            .forEach(p -> list.add(p.getFileName().toString()));
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    /**
     * Returns the Minecraft game directory (the folder containing saves/,
     * resourcepacks/, etc.). Falls back to {@code Path.of(".")} when
     * Minecraft is not yet initialised.
     */
    private static java.nio.file.Path gameDirectory() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gameDirectory != null) {
            return minecraft.gameDirectory.toPath();
        }
        return java.nio.file.Path.of(".");
    }

    @Nullable
    private static byte[] readFromResourceManager(@NotNull Identifier id) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                return null;
            }
            if (minecraft.getResourceManager() == null) {
                return null;
            }
            Optional<Resource> opt = minecraft.getResourceManager().getResource(id);
            if (opt.isEmpty()) {
                return null;
            }
            try (InputStream stream = opt.get().open()) {
                return stream.readAllBytes();
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not read font resource {}", id, exception);
            return null;
        }
    }

    @Nullable
    private static byte[] readFromClassloader(@NotNull Identifier id) {
        String path = "assets/" + id.getNamespace() + "/" + id.getPath();
        try (InputStream stream = FontLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return null;
            }
            return stream.readAllBytes();
        } catch (IOException exception) {
            LOGGER.warn("Could not read font resource {}", id, exception);
            return null;
        }
    }

    @Nullable
    private static byte[] readFromVanishProvider(@NotNull Identifier id) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> clazz = null;
            if (cl != null) {
                try {
                    clazz = Class.forName("dev.technix.client.core.VanishResourceProvider", true, cl);
                } catch (ClassNotFoundException ignored) {
                }
            }
            if (clazz == null) {
                clazz = Class.forName("dev.technix.client.core.VanishResourceProvider");
            }
            java.lang.reflect.Method m = clazz.getMethod("getBytes", Identifier.class);
            return (byte[]) m.invoke(null, id);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
