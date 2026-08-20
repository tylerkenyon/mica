package dev.technix.mica.api;

import imgui.ImColor;


public class Palette {

    protected Palette() {
    }

    

    
    public static final int PANEL = ImColor.rgba(0, 0, 0, 158);

    
    public static final int PANEL_LIGHT = ImColor.rgba(18, 20, 26, 120);

    
    public static final int BORDER = ImColor.rgba(255, 255, 255, 28);

    
    public static final int SLOT = ImColor.rgba(0, 0, 0, 45);

    
    public static final int TRACK = ImColor.rgba(0, 0, 0, 155);

    

    
    public static final int ACCENT = ImColor.rgba(71, 148, 253, 255);

    
    public static final int ACCENT_ALT = ImColor.rgba(240, 130, 170, 255);

    

    public static final int TEXT = ImColor.rgba(255, 255, 255, 255);
    
    public static final int TEXT_STRONG = ImColor.rgba(255, 255, 255, 235);
    
    public static final int TEXT_DIM = ImColor.rgba(255, 255, 255, 110);
    
    public static final int TEXT_FAINT = ImColor.rgba(255, 255, 255, 95);
    
    public static final int DIVIDER = ImColor.rgba(255, 255, 255, 38);

    
    public static int withAlphaScale(int color, float factor) {
        int alpha = (color >>> 24) & 0xFF;
        int scaled = Math.clamp((int) (alpha * factor), 0, 255);
        return (color & 0x00FFFFFF) | (scaled << 24);
    }

    
    public static int lerpColor(int from, int to, float t) {
        if (Float.isNaN(t)) {
            return from;
        }
        float clamped = Math.clamp(t, 0.0f, 1.0f);
        int a = lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, clamped);
        int r = lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, clamped);
        int g = lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, clamped);
        int b = lerp(from & 0xFF, to & 0xFF, clamped);
        return ImColor.rgba(r, g, b, a);
    }

    private static int lerp(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }
}
