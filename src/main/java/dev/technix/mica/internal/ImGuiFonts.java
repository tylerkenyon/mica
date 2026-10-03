package dev.technix.mica.internal;

import dev.technix.mica.api.FontFace;
import dev.technix.mica.api.FontRegistry;
import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class ImGuiFonts {

    private static final Logger LOGGER = LoggerFactory.getLogger("mica");


    private static final float TEXT_SIZE = 13.0f;
    private static final float LOGO_SIZE = 18.0f;

    
    private static final Map<String, FontFace> FACES = new HashMap<>();

    
    private static final List<FontRegistry> REGISTRIES = new ArrayList<>();

    private static boolean loaded;

    private ImGuiFonts() {
    }

    
    public static synchronized void load(@org.jetbrains.annotations.NotNull List<FontRegistry> registries) {
        if (loaded) {
            
            for (FontRegistry registry : registries) {
                if (!REGISTRIES.contains(registry)) {
                    REGISTRIES.add(registry);
                    for (FontFace face : registry.all()) {
                        FACES.put(face.name(), face);
                    }
                }
            }
            return;
        }
        loaded = true;

        ImFontAtlas atlas = ImGui.getIO().getFonts();
        atlas.addFontDefault();

        
        
        for (FontRegistry registry : registries) {
            REGISTRIES.add(registry);
            registry.rasterizeInto(atlas);
            for (FontFace face : registry.all()) {
                FACES.put(face.name(), face);
            }
        }

        FontFace regular = addDefaultFace(atlas, SystemFonts.Weight.REGULAR, FontFace.REGULAR, TEXT_SIZE);
        FontFace medium = addDefaultFace(atlas, SystemFonts.Weight.MEDIUM, FontFace.MEDIUM, TEXT_SIZE);
        FontFace bold = addDefaultFace(atlas, SystemFonts.Weight.BOLD, FontFace.BOLD, TEXT_SIZE);
        FontFace logo = addDefaultFace(atlas, SystemFonts.Weight.MEDIUM, FontFace.LOGO, LOGO_SIZE);

        FACES.put(regular.name(), regular);
        FACES.put(medium.name(), medium);
        FACES.put(bold.name(), bold);
        FACES.put(logo.name(), logo);

        LOGGER.info("Mica fonts loaded: system regular={} medium={} bold={} logo={}, user faces={}",
                regular != null, medium != null, bold != null, logo != null,
                countUserFaces());
    }


    public static synchronized void reload() {
        if (!loaded) {
            return;
        }
        ImFontAtlas atlas = ImGui.getIO().getFonts();
        atlas.clear();
        atlas.addFontDefault();

        FACES.clear();
        for (FontRegistry registry : REGISTRIES) {
            registry.rasterizeInto(atlas);
            for (FontFace face : registry.all()) {
                FACES.put(face.name(), face);
            }
        }
        FontFace regular = addDefaultFace(atlas, SystemFonts.Weight.REGULAR, FontFace.REGULAR, TEXT_SIZE);
        FontFace medium = addDefaultFace(atlas, SystemFonts.Weight.MEDIUM, FontFace.MEDIUM, TEXT_SIZE);
        FontFace bold = addDefaultFace(atlas, SystemFonts.Weight.BOLD, FontFace.BOLD, TEXT_SIZE);
        FontFace logo = addDefaultFace(atlas, SystemFonts.Weight.MEDIUM, FontFace.LOGO, LOGO_SIZE);
        FACES.put(regular.name(), regular);
        FACES.put(medium.name(), medium);
        FACES.put(bold.name(), bold);
        FACES.put(logo.name(), logo);
        LOGGER.info("Mica fonts reloaded.");
    }

    private static int countUserFaces() {
        int count = 0;
        for (FontRegistry registry : REGISTRIES) {
            count += registry.all().size();
        }
        return count;
    }

    /**
     * Rasterises one of Mica's default faces from the user's override if there is one, otherwise from
     * the operating system's UI font.
     *
     * <p>Mica bundles no font. It used to ship SF Pro Display, which is Apple-licensed and not
     * redistributable under an open-source licence, so the default now comes from whatever the platform
     * already installed for its own interface - see {@link SystemFonts}, including its note that a
     * platform without a distinct bold file resolves bold to regular.
     *
     * <p>A missing face is not fatal. The returned {@link FontFace} carries a null {@code ImFont}, the
     * same as it always did for a font that failed to rasterise, and callers already handle that.
     */
    private static FontFace addDefaultFace(ImFontAtlas atlas, SystemFonts.Weight weight,
                                           String faceName, float sizePixels) {
        final int rounded = Math.max(1, (int) Math.round(sizePixels));
        byte[] data = FontLoader.readFromFontsDir(overrideFileName(weight));
        if (data == null) {
            data = SystemFonts.read(weight);
        }
        if (data == null) {
            LOGGER.warn("No {} face available - no {} in {} and no system font found",
                    faceName, overrideFileName(weight), "<gamedir>/mica/fonts");
            return new FontFace(faceName, rounded, null);
        }
        try {
            ImFont font = atlas.addFontFromMemoryTTF(data, rounded);
            return new FontFace(faceName, rounded, font);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not rasterise the {} face at {}px", faceName, sizePixels, exception);
            return new FontFace(faceName, rounded, null);
        }
    }

    /**
     * The file name that overrides a default face when dropped in {@code <gamedir>/mica/fonts}.
     *
     * <p>One fixed name per weight rather than a search: a user replacing the overlay's font wants to
     * know exactly where to put it, and a guessing game over extensions would make "why is my font not
     * loading" a support question.
     */
    private static String overrideFileName(SystemFonts.Weight weight) {
        return switch (weight) {
            case REGULAR -> "system-ui.ttf";
            case MEDIUM -> "system-ui-medium.ttf";
            case BOLD -> "system-ui-bold.ttf";
        };
    }

    @org.jetbrains.annotations.NotNull
    public static FontFace regular() {
        return FACES.get(FontFace.REGULAR);
    }

    @org.jetbrains.annotations.NotNull
    public static FontFace medium() {
        return FACES.get(FontFace.MEDIUM);
    }

    @org.jetbrains.annotations.NotNull
    public static FontFace bold() {
        return FACES.get(FontFace.BOLD);
    }

    @org.jetbrains.annotations.NotNull
    public static FontFace logo() {
        return FACES.get(FontFace.LOGO);
    }


    @org.jetbrains.annotations.Nullable
    public static FontFace byName(@org.jetbrains.annotations.NotNull String name) {
        return FACES.get(name);
    }


    @org.jetbrains.annotations.NotNull
    public static Collection<FontFace> all() {
        return Collections.unmodifiableCollection(FACES.values());
    }


    public static boolean push(@org.jetbrains.annotations.Nullable FontFace face) {
        if (face == null || face.imFont() == null) {
            return false;
        }
        ImGui.pushFont(face.imFont(), face.pixelSize());
        return true;
    }


    public static void pop(boolean pushed) {
        if (pushed) {
            ImGui.popFont();
        }
    }
}
