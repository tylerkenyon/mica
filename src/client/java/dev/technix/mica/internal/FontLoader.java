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
            fromManager = readFromClassloader(id);
        }
        if (fromManager == null) {
            LOGGER.warn("Font resource missing: {}", id);
            return null;
        }
        CACHE.put(id, fromManager);
        return fromManager;
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
}
