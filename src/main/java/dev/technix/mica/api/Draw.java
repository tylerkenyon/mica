package dev.technix.mica.api;

import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;


/**
 * Static drawing primitives for overlay elements: frosted glass panels, rounded
 * rectangles, images/atlas sprites and text rendered through a pushed
 * {@link FontFace}.
 *
 * <p>All methods draw onto the {@link RenderContext}'s ImGui draw list for the
 * current frame and never mutate global ImGui widget state beyond pushing/popping
 * fonts while measuring or drawing text.</p>
 *
 * <p>Text helpers obtain their font via {@link MicaFonts#push(FontFace)} so callers
 * never need to reach into the platform internals.</p>
 */
public final class Draw {

    private static final int WHITE = ImColor.rgba(255, 255, 255, 255);

    private Draw() {
    }


    /**
     * Draws a frosted panel using the context's default style: backdrop blur,
     * tinted rounded fill and default border.
     */
    public static void frostedPanel(@NotNull RenderContext context,
                                    float x, float y, float width, float height) {
        FrostedGlassStyle style = context.glassStyle();
        frostedPanel(context, x, y, width, height,
                style.defaultRounding(), style.defaultTint(), style.defaultBorder());
    }


    /**
     * Draws a frosted panel with an explicit rounding radius and the context's
     * default tint and border.
     */
    public static void frostedPanel(@NotNull RenderContext context,
                                    float x, float y, float width, float height,
                                    float rounding) {
        FrostedGlassStyle style = context.glassStyle();
        frostedPanel(context, x, y, width, height, rounding,
                style.defaultTint(), style.defaultBorder());
    }


    /**
     * Draws a frosted panel: blur backdrop (when available), tinted rounded rect
     * and, if {@code border} is non-zero, a 1px rounded outline.
     */
    public static void frostedPanel(@NotNull RenderContext context,
                                    float x, float y, float width, float height,
                                    float rounding, int tint, int border) {
        backdrop(context, x, y, width, height, rounding);
        roundedRect(context, x, y, width, height, rounding, tint);
        if (border != 0) {
            roundedRectOutline(context, x, y, width, height, rounding, border, 1.0f);
        }
    }


    /**
     * Draws the blurred-screen backdrop quad for the given rect.
     *
     * @return {@code false} when blur is unavailable (non-Vulkan host, first
     *         frames before the blur texture exists) so callers can fall back
     *         to an opaque fill.
     */
    public static boolean backdrop(@NotNull RenderContext context,
                                   float x, float y, float width, float height, float rounding) {
        if (!context.hasBlur() || context.width() <= 0.0f || context.height() <= 0.0f) {
            return false;
        }
        float screenWidth = context.width();
        float screenHeight = context.height();
        context.drawList().addImageRounded(context.blurTextureId(),
                x, y, x + width, y + height,
                x / screenWidth, (screenHeight - y) / screenHeight,
                (x + width) / screenWidth, (screenHeight - (y + height)) / screenHeight,
                WHITE, rounding);
        return true;
    }


    /**
     * Filled rectangle with per-corner rounding.
     */
    public static void roundedRect(@NotNull RenderContext context,
                                   float x, float y, float width, float height,
                                   float rounding, int color) {
        context.drawList().addRectFilled(x, y, x + width, y + height, color, rounding);
    }


    /**
     * Rectangle outline with per-corner rounding and explicit thickness.
     */
    public static void roundedRectOutline(@NotNull RenderContext context,
                                          float x, float y, float width, float height,
                                          float rounding, int color, float thickness) {
        context.drawList().addRect(x, y, x + width, y + height, color, rounding, 0, thickness);
    }


    /**
     * Vertical 1px divider centred on {@code centerY}.
     */
    public static void verticalDivider(@NotNull RenderContext context,
                                       float x, float centerY, float height, int color) {
        context.drawList().addLine(x, centerY - height * 0.5f,
                x, centerY + height * 0.5f, color, 1.0f);
    }


    /**
     * Progress bar: rounded track plus a pill-shaped fill clamped to
     * {@code [0, 1]}. The fill never renders narrower than the bar height.
     */
    public static void progressBar(@NotNull RenderContext context,
                                   float x, float y, float width, float height,
                                   float progress, int trackColor, int fillColor) {
        float rounding = height * 0.5f;
        roundedRect(context, x, y, width, height, rounding, trackColor);

        float clamped = Math.clamp(progress, 0.0f, 1.0f);
        if (clamped <= 0.0f) {
            return;
        }
        // Pill-shaped fill: minimum width equals the bar height so the caps stay round.
        float fillWidth = Math.max(clamped * width, height);
        roundedRect(context, x, y, fillWidth, height, rounding, fillColor);
    }


    /**
     * Draws a registered {@link TextureHandle} stretched over the given rect.
     *
     * @return {@code false} when the handle is null or its texture id is zero
     *         (nothing is drawn).
     */
    public static boolean image(@NotNull RenderContext context,
                                @Nullable TextureHandle handle,
                                float x, float y, float width, float height) {
        if (handle == null || handle.imGuiTextureId() == 0L) {
            return false;
        }
        // Full UV range: the whole registered texture maps onto the rect.
        context.drawList().addImage(handle.imGuiTextureId(),
                x, y, x + width, y + height,
                0f, 0f, 1f, 1f);
        return true;
    }


    /**
     * Draws a raw ImGui texture id over the given rect with explicit UVs and
     * optional corner rounding ({@code rounding > 0} selects the rounded path).
     *
     * @return {@code false} when {@code textureId} is zero (nothing is drawn).
     */
    public static boolean image(@NotNull RenderContext context, long textureId,
                                float x, float y, float width, float height,
                                float u0, float v0, float u1, float v1, float rounding) {
        if (textureId == 0L) {
            return false;
        }
        ImDrawList drawList = context.drawList();
        if (rounding > 0.0f) {
            drawList.addImageRounded(textureId, x, y, x + width, y + height,
                    u0, v0, u1, v1, WHITE, rounding);
        } else {
            drawList.addImage(textureId, x, y, x + width, y + height, u0, v0, u1, v1);
        }
        return true;
    }


    /**
     * Draws {@code text} at the top-left position using the given font face.
     */
    public static void text(@NotNull RenderContext context, @NotNull FontFace face, int color,
                            @NotNull String text, float x, float y) {
        boolean pushed = MicaFonts.push(face);
        context.drawList().addText(x, y, color, text);
        MicaFonts.pop(pushed);
    }


    /**
     * Draws {@code text} horizontally at {@code x}, vertically centred on
     * {@code centerY}.
     */
    public static void textVCentered(@NotNull RenderContext context, @NotNull FontFace face, int color,
                                     @NotNull String text, float x, float centerY) {
        boolean pushed = MicaFonts.push(face);
        context.drawList().addText(x, centerY - ImGui.getTextLineHeight() * 0.5f, color, text);
        MicaFonts.pop(pushed);
    }


    /**
     * Draws {@code text} centred both ways around {@code (centerX, centerY)}.
     */
    public static void textCentered(@NotNull RenderContext context, @NotNull FontFace face, int color,
                                    @NotNull String text, float centerX, float centerY) {
        boolean pushed = MicaFonts.push(face);
        float width = ImGui.calcTextSizeX(text);
        float height = ImGui.getTextLineHeight();
        context.drawList().addText(centerX - width * 0.5f, centerY - height * 0.5f, color, text);
        MicaFonts.pop(pushed);
    }


    /**
     * Measures {@code text} in the given font face.
     */
    public static float textWidth(@NotNull FontFace face, @NotNull String text) {
        boolean pushed = MicaFonts.push(face);
        float width = ImGui.calcTextSizeX(text);
        MicaFonts.pop(pushed);
        return width;
    }


    /**
     * Current line height of the given font face.
     */
    public static float textHeight(@NotNull FontFace face) {
        boolean pushed = MicaFonts.push(face);
        float height = ImGui.getTextLineHeight();
        MicaFonts.pop(pushed);
        return height;
    }


    /**
     * Registers a sprite region of a host Minecraft atlas for direct drawing.
     *
     * @return the texture id plus normalised UV bounds, or empty when the atlas
     *         sprite could not be registered.
     */
    @NotNull
    public static Optional<AtlasSpriteRef> registerSprite(@NotNull MinecraftCompat compat,
                                                          @NotNull SpriteBounds sprite,
                                                          @NotNull TextureFilter filter,
                                                          @NotNull OverlayRenderer renderer) {
        Optional<TextureHandle> handle = renderer.registerAtlasTexture(sprite.atlasId(), filter);
        if (handle.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AtlasSpriteRef(handle.get().imGuiTextureId(),
                sprite.u0(), sprite.v0(), sprite.u1(), sprite.v1()));
    }


    /**
     * Immutable snapshot of a drawable atlas sprite: raw ImGui texture id plus
     * the normalised UV rect inside the host atlas.
     */
    public record AtlasSpriteRef(long textureId, float u0, float v0, float u1, float v1) {
    }


    // ------------------------------------------------------------------
    // Text helpers
    // ------------------------------------------------------------------

    /**
     * Draws text with a drop shadow behind it. The shadow is offset by
     * {@code shadowOffset} pixels in both X and Y and rendered at 70%
     * of the text's alpha blended onto black.
     */
    public static void textWithShadow(@NotNull RenderContext context, @NotNull FontFace face,
                                       int color, @NotNull String text, float x, float y,
                                       float shadowOffset) {
        boolean pushed = MicaFonts.push(face);
        int textAlpha = (color >>> 24) & 0xFF;
        int shadowAlpha = Math.round(textAlpha * 0.70f);
        int shadowColor = (shadowAlpha << 24); // black with scaled alpha
        context.drawList().addText(x + shadowOffset, y + shadowOffset, shadowColor, text);
        context.drawList().addText(x, y, color, text);
        MicaFonts.pop(pushed);
    }


    /**
     * Draws text right-aligned: the text's right edge sits at {@code rightX}.
     */
    public static void textRight(@NotNull RenderContext context, @NotNull FontFace face,
                                  int color, @NotNull String text, float rightX, float y) {
        boolean pushed = MicaFonts.push(face);
        float width = ImGui.calcTextSizeX(text);
        context.drawList().addText(rightX - width, y, color, text);
        MicaFonts.pop(pushed);
    }


    // ------------------------------------------------------------------
    // Clip rect helpers
    // ------------------------------------------------------------------

    /**
     * Pushes a scissor clip rectangle onto the draw list, restricting
     * subsequent draw commands to the given bounds.
     *
     * @param intersect if {@code true}, the new rect is intersected with
     *                  the current clip rect; otherwise it replaces it.
     */
    public static void pushClipRect(@NotNull RenderContext context,
                                     float x, float y, float width, float height,
                                     boolean intersect) {
        context.drawList().pushClipRect(x, y, x + width, y + height, intersect);
    }

    /**
     * Pops the most recently pushed clip rectangle from the draw list.
     */
    public static void popClipRect(@NotNull RenderContext context) {
        context.drawList().popClipRect();
    }
}
