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
    private final Map<String, PendingSource> pendingSources = new LinkedHashMap<>();

    private record PendingSource(Identifier resourceLocation, int sizePixels) {
    }

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
        this.pendingSources.put(name,
                new PendingSource(resourceLocation, requestedSize));
        ImFont imFont = rasteriseIfAtlasAlive(data, requestedSize, name);
        FontFace face = new FontFace(name, requestedSize, imFont);
        faces.put(name, face);
        return face;
    }

    /**
     * Re-rasterises every registered face from its source into the given
     * (freshly cleared) atlas and replaces the stored faces with fonted
     * versions. Called by ImGuiFonts on load/reload so faces registered
     * before the ImGui context existed gain real glyphs.
     */
    public void rasterizeInto(@NotNull imgui.ImFontAtlas atlas) {
        atlas.setTexMinWidth(2048);
        for (var entry : this.pendingSources.entrySet()) {
            byte[] data = FontLoader.read(entry.getValue().resourceLocation());
            if (data == null) {
                continue;
            }
            try {
                ImFont font = atlas.addFontFromMemoryTTF(data,
                        entry.getValue().sizePixels());
                this.faces.put(entry.getKey(), new FontFace(entry.getKey(),
                        entry.getValue().sizePixels(), font));
            } catch (RuntimeException exception) {
                LoggerFactory.getLogger("mica").warn(
                        "Could not rasterise user font {} at {}px", entry.getKey(),
                        entry.getValue().sizePixels(), exception);
            }
        }
    }

    @Nullable
    private static ImFont rasteriseIfAtlasAlive(byte[] data, int size, String name) {
        // ImGui's fatal assert fires when no context exists yet — the context is
        // created lazily on the first rendered frame, so check before touching IO.
        // NB: getCurrentContext() wraps even a null native pointer in a Java object;
        // the pointer itself is the null indicator.
        if (ImGui.getCurrentContext().isNotValidPtr()) {
            // Expected, self-healing path: the face is stored as a pending source in add()
            // and gets real glyphs later via rasterizeInto() on the first reload(). DEBUG,
            // not WARN — nothing is wrong, and at startup this fires for every registered face.
            LOGGER.debug("Font {} queued before ImGui context is alive; will commit via ImGuiFonts.reload().",
                    name);
            return null;
        }
        try {
            ImFontAtlas atlas = ImGui.getIO().getFonts();
            if (atlas == null) {
                LOGGER.debug("Font {} queued before the font atlas is available; will commit via ImGuiFonts.reload().",
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

    /**
     * File names of the font files the user has dropped in {@code <gamedir>/mica/fonts}, so a consumer
     * can offer them as choices rather than hardcoding a list.
     *
     * <p>Static, and deliberately not derived from {@link #root()}: that directory is one fixed
     * location shared by every registry, not a per-registry path, and an instance method would imply
     * it varied. Names come back as bare file names - {@link #add} takes exactly that.
     *
     * <p>Promoted out of {@code dev.technix.mica.internal.FontLoader}. Enumerating the user's own
     * fonts is a consumer-facing question, and having the only caller reach into {@code internal} for
     * it was the last thing keeping the client welded to Mica's private surface.
     */
    @NotNull
    public static List<String> listExternalFonts() {
        return FontLoader.listExternalFonts();
    }

    public void commitPendingFaces() {
        if (faces.isEmpty()) {
            return;
        }
        ImGuiFonts.reload();
    }
}
