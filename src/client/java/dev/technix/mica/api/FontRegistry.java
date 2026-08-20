package dev.technix.mica.api;

import dev.technix.mica.internal.FontLoader;
import dev.technix.mica.internal.ImGuiFonts;
import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImGui;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class FontRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("mica");

    private final Identifier root;
    private final Map<String, FontFace> faces = new LinkedHashMap<>();

    public FontRegistry(@NotNull Identifier root) {
        this.root = root;
    }

    @NotNull
    public Identifier root() {
        return root;
    }

    @Nullable
    public FontFace add(@NotNull String name, @NotNull String fileName, float sizePixels) {
        Identifier resourceLocation = Identifier.fromNamespaceAndPath(root.getNamespace(),
                root.getPath() + "/" + fileName);
        byte[] data = FontLoader.read(resourceLocation);
        if (data == null) {
            return null;
        }
        int requestedSize = Math.max(1, (int) Math.round(sizePixels));
        ImFont imFont = rasteriseIfAtlasAlive(data, requestedSize, name);
        FontFace face = new FontFace(name, requestedSize, imFont);
        faces.put(name, face);
        return face;
    }

    @Nullable
    private static ImFont rasteriseIfAtlasAlive(byte[] data, int size, String name) {
        try {
            ImFontAtlas atlas = ImGui.getIO().getFonts();
            if (atlas == null) {
                LOGGER.warn("Font {} added before ImGui context is alive; commit via ImGuiFonts.reload().",
                        name);
                return null;
            }
            return atlas.addFontFromMemoryTTF(data, size);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not rasterise {} at {}px", name, size, exception);
            return null;
        }
    }

    @Nullable
    public FontFace get(@NotNull String name) {
        return faces.get(name);
    }

    @NotNull
    public Collection<FontFace> all() {
        List<FontFace> snapshot = new ArrayList<>(faces.values());
        return Collections.unmodifiableList(snapshot);
    }

    public void commitPendingFaces() {
        if (faces.isEmpty()) {
            return;
        }
        ImGuiFonts.reload();
    }
}
