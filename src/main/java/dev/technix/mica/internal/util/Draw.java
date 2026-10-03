package dev.technix.mica.internal.util;

import dev.technix.mica.api.FontFace;
import dev.technix.mica.api.MinecraftCompat;
import dev.technix.mica.api.OverlayRenderer;
import dev.technix.mica.api.RenderContext;
import dev.technix.mica.api.SpriteBounds;
import dev.technix.mica.api.TextureFilter;
import dev.technix.mica.api.TextureHandle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;


/**
 * Deprecated shim — use {@link dev.technix.mica.api.Draw}.
 *
 * <p>Every method forwards 1:1 to the public API class; kept only so existing
 * callers keep compiling until they migrate their imports.</p>
 */
public final class Draw {

    private Draw() {
    }


    /**
     * Deprecated shim — use {@link dev.technix.mica.api.Draw#frostedPanel(RenderContext, float, float, float, float)}.
     */
    public static void frostedPanel(@NotNull RenderContext context,
                                    float x, float y, float width, float height) {
        dev.technix.mica.api.Draw.frostedPanel(context, x, y, width, height);
    }


    /**
     * Deprecated shim — use {@link dev.technix.mica.api.Draw#frostedPanel(RenderContext, float, float, float, float, float)}.
     */
    public static void frostedPanel(@NotNull RenderContext context,
                                    float x, float y, float width, float height,
                                    float rounding) {
        dev.technix.mica.api.Draw.frostedPanel(context, x, y, width, height, rounding);
    }


    /**
     * Deprecated shim — use {@link dev.technix.mica.api.Draw#frostedPanel(RenderContext, float, float, float, float, float, int, int)}.
     */
    public static void frostedPanel(@NotNull RenderContext context,
                                    float x, float y, float width, float height,
                                    float rounding, int tint, int border) {
        dev.technix.mica.api.Draw.frostedPanel(context, x, y, width, height, rounding, tint, border);
    }


    /**
     * Deprecated shim — use {@link dev.technix.mica.api.Draw#backdrop(RenderContext, float, float, float, float, float)}.
     */
    public static boolean backdrop(@NotNull RenderContext context,
                                   float x, float y, float width, float height, float rounding) {
        return dev.technix.mica.api.Draw.backdrop(context, x, y, width, height, rounding);
    }


    /**
     * Deprecated shim — use {@link dev.technix.mica.api.Draw#roundedRect}.
     */
    public static void roundedRect(@NotNull RenderContext context,
                                   float x, float y, float width, float height,
                                   float rounding, int color) {
        dev.technix.mica.api.Draw.roundedRect(context, x, y, width, height, rounding, color);
    }


    /**
     * Deprecated shim — use {@link dev.technix.mica.api.Draw#roundedRectOutline}.
     */
    public static void roundedRectOutline(@NotNull RenderContext context,
                                          float x, float y, float width, float height,
                                          float rounding, int color, float thickness) {
        dev.technix.mica.api.Draw.roundedRectOutline(context, x, y, width, height, rounding, color, thickness);
    }


    /**
     * Deprecated shim — use {@link dev.technix.mica.api.Draw#verticalDivider}.
     */
    public static void verticalDivider(@NotNull RenderContext context,
                                       float x, float centerY, float height, int color) {
        dev.technix.mica.api.Draw.verticalDivider(context, x, centerY, height, color);
    }


    /**
     * Deprecated shim — use {@link dev.technix.mica.api.Draw#progressBar}.
     */
    public static void progressBar(@NotNull RenderContext context,
                                   float x, float y, float width, float height,
                                   float progress, int trackColor, int fillColor) {
        dev.technix.mica.api.Draw.progressBar(context, x, y, width, height, progress, trackColor, fillColor);
    }


    /**
     * Deprecated shim — use {@link dev.technix.mica.api.Draw#image(RenderContext, TextureHandle, float, float, float, float)}.
     */
    public static boolean image(@NotNull RenderContext context,
                                @Nullable TextureHandle handle,
                                float x, float y, float width, float height) {
        return dev.technix.mica.api.Draw.image(context, handle, x, y, width, height);
    }


    /**
     * Deprecated shim — use {@link dev.technix.mica.api.Draw#image(RenderContext, long, float, float, float, float, float, float, float, float, float)}.
     */
    public static boolean image(@NotNull RenderContext context, long textureId,
                                float x, float y, float width, float height,
                                float u0, float v0, float u1, float v1, float rounding) {
        return dev.technix.mica.api.Draw.image(context, textureId, x, y, width, height,
                u0, v0, u1, v1, rounding);
    }


    /**
     * Deprecated shim — use {@link dev.technix.mica.api.Draw#text}.
     */
    public static void text(@NotNull RenderContext context, @NotNull FontFace face, int color,
                            @NotNull String text, float x, float y) {
        dev.technix.mica.api.Draw.text(context, face, color, text, x, y);
    }


    /**
     * Deprecated shim — use {@link dev.technix.mica.api.Draw#textVCentered}.
     */
    public static void textVCentered(@NotNull RenderContext context, @NotNull FontFace face, int color,
                                     @NotNull String text, float x, float centerY) {
        dev.technix.mica.api.Draw.textVCentered(context, face, color, text, x, centerY);
    }


    /**
     * Deprecated shim — use {@link dev.technix.mica.api.Draw#textCentered}.
     */
    public static void textCentered(@NotNull RenderContext context, @NotNull FontFace face, int color,
                                    @NotNull String text, float centerX, float centerY) {
        dev.technix.mica.api.Draw.textCentered(context, face, color, text, centerX, centerY);
    }


    /**
     * Deprecated shim — use {@link dev.technix.mica.api.Draw#textWidth(FontFace, String)}.
     */
    public static float textWidth(@NotNull FontFace face, @NotNull String text) {
        return dev.technix.mica.api.Draw.textWidth(face, text);
    }


    /**
     * Deprecated shim — use {@link dev.technix.mica.api.Draw#textHeight(FontFace)}.
     */
    public static float textHeight(@NotNull FontFace face) {
        return dev.technix.mica.api.Draw.textHeight(face);
    }


    /**
     * Deprecated shim — use {@link dev.technix.mica.api.Draw#registerSprite}.
     */
    @NotNull
    public static Optional<dev.technix.mica.api.Draw.AtlasSpriteRef> registerSprite(
            @NotNull MinecraftCompat compat,
            @NotNull SpriteBounds sprite,
            @NotNull TextureFilter filter,
            @NotNull OverlayRenderer renderer) {
        return dev.technix.mica.api.Draw.registerSprite(compat, sprite, filter, renderer);
    }
}
