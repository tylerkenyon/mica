package dev.technix.mica.api;

import dev.technix.mica.internal.FontLoader;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;


/**
 * Public resource-reading facade: loads a raw asset from the Minecraft resource
 * manager, falling back to the classpath (so resources bundled inside a /libs/
 * jar resolve too). Results are cached per identifier.
 *
 * <p>Intended for reading font binaries and other small assets that overlay
 * elements need as bytes.</p>
 */
public final class ResourceReader {

    private ResourceReader() {
    }


    /**
     * Reads the asset at {@code id} (e.g.
     * {@code Identifier.fromNamespaceAndPath("mymod", "font/myfont.ttf")}).
     *
     * @return the raw bytes, or null when {@code id} is null or the resource is
     *         missing.
     */
    @Nullable
    public static byte[] read(@Nullable Identifier id) {
        return FontLoader.read(id);
    }
}
