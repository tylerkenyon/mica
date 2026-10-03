package dev.technix.mica.internal.util;

import dev.technix.mica.api.Palette;


public final class Theme {

    private Theme() {
    }

    
    public static Palette palette() {
        return PaletteConstants.INSTANCE;
    }

    
    private static final class PaletteConstants extends Palette {
        static final PaletteConstants INSTANCE = new PaletteConstants();
        private PaletteConstants() {
        }
    }

    
    public static final int PANEL = Palette.PANEL;
    public static final int PANEL_LIGHT = Palette.PANEL_LIGHT;
    public static final int BORDER = Palette.BORDER;
    public static final int SLOT = Palette.SLOT;
    public static final int TRACK = Palette.TRACK;
    public static final int ACCENT = Palette.ACCENT;
    public static final int ACCENT_ALT = Palette.ACCENT_ALT;
    public static final int TEXT = Palette.TEXT;
    public static final int TEXT_STRONG = Palette.TEXT_STRONG;
    public static final int TEXT_DIM = Palette.TEXT_DIM;
    public static final int TEXT_FAINT = Palette.TEXT_FAINT;
    public static final int DIVIDER = Palette.DIVIDER;

    public static int withAlphaScale(int color, float factor) {
        return Palette.withAlphaScale(color, factor);
    }

    public static int lerpColor(int from, int to, float t) {
        return Palette.lerpColor(from, to, t);
    }
}
