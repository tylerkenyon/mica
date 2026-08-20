package dev.technix.mica.internal.util;

import dev.technix.mica.api.FontFace;
import dev.technix.mica.api.FrostedGlassStyle;
import dev.technix.mica.api.Palette;
import dev.technix.mica.api.RenderContext;
import dev.technix.mica.api.TextureFilter;
import dev.technix.mica.api.TextureHandle;
import dev.technix.mica.internal.ImGuiFonts;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;


public final class Draw {

    private static final int WHITE = ImColor.rgba(255, 255, 255, 255);

    private Draw() {
    }

    
    
    

    
    public static void frostedPanel(@NotNull RenderContext context,
                                    float x, float y, float width, float height) {
        FrostedGlassStyle style = context.glassStyle();
        frostedPanel(context, x, y, width, height,
                style.defaultRounding(), style.defaultTint(), style.defaultBorder());
    }

    
    public static void frostedPanel(@NotNull RenderContext context,
                                    float x, float y, float width, float height,
                                    float rounding) {
        FrostedGlassStyle style = context.glassStyle();
        frostedPanel(context, x, y, width, height, rounding,
                style.defaultTint(), style.defaultBorder());
    }

    
    public static void frostedPanel(@NotNull RenderContext context,
                                    float x, float y, float width, float height,
                                    float rounding, int tint, int border) {
        backdrop(context, x, y, width, height, rounding);
        roundedRect(context, x, y, width, height, rounding, tint);
        if (border != 0) {
            roundedRectOutline(context, x, y, width, height, rounding, border, 1.0f);
        }
    }


    
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

    
    
    

    public static void roundedRect(@NotNull RenderContext context,
                                   float x, float y, float width, float height,
                                   float rounding, int color) {
        context.drawList().addRectFilled(x, y, x + width, y + height, color, rounding);
    }

    public static void roundedRectOutline(@NotNull RenderContext context,
                                          float x, float y, float width, float height,
                                          float rounding, int color, float thickness) {
        context.drawList().addRect(x, y, x + width, y + height, color, rounding, 0, thickness);
    }

    public static void verticalDivider(@NotNull RenderContext context,
                                       float x, float centerY, float height, int color) {
        context.drawList().addLine(x, centerY - height * 0.5f,
                x, centerY + height * 0.5f, color, 1.0f);
    }

    
    public static void progressBar(@NotNull RenderContext context,
                                   float x, float y, float width, float height,
                                   float progress, int trackColor, int fillColor) {
        float rounding = height * 0.5f;
        roundedRect(context, x, y, width, height, rounding, trackColor);

        float clamped = Math.clamp(progress, 0.0f, 1.0f);
        if (clamped <= 0.0f) {
            return;
        }
        
        
        float fillWidth = Math.max(clamped * width, height);
        roundedRect(context, x, y, fillWidth, height, rounding, fillColor);
    }

    
    
    

    
    public static boolean image(@NotNull RenderContext context,
                                @Nullable TextureHandle handle,
                                float x, float y, float width, float height) {
        if (handle == null || handle.imGuiTextureId() == 0L) {
            return false;
        }
        
        
        
        context.drawList().addImage(handle.imGuiTextureId(),
                x, y, x + width, y + height,
                0f, 0f, 1f, 1f);
        return true;
    }

    
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

    
    
    

    
    public static void text(@NotNull RenderContext context, @NotNull FontFace face, int color,
                            @NotNull String text, float x, float y) {
        boolean pushed = ImGuiFonts.push(face);
        context.drawList().addText(x, y, color, text);
        ImGuiFonts.pop(pushed);
    }

    
    public static void textVCentered(@NotNull RenderContext context, @NotNull FontFace face, int color,
                                     @NotNull String text, float x, float centerY) {
        boolean pushed = ImGuiFonts.push(face);
        context.drawList().addText(x, centerY - ImGui.getTextLineHeight() * 0.5f, color, text);
        ImGuiFonts.pop(pushed);
    }

    
    public static void textCentered(@NotNull RenderContext context, @NotNull FontFace face, int color,
                                    @NotNull String text, float centerX, float centerY) {
        boolean pushed = ImGuiFonts.push(face);
        float width = ImGui.calcTextSizeX(text);
        float height = ImGui.getTextLineHeight();
        context.drawList().addText(centerX - width * 0.5f, centerY - height * 0.5f, color, text);
        ImGuiFonts.pop(pushed);
    }

    
    public static float textWidth(@NotNull FontFace face, @NotNull String text) {
        boolean pushed = ImGuiFonts.push(face);
        float width = ImGui.calcTextSizeX(text);
        ImGuiFonts.pop(pushed);
        return width;
    }

    
    public static float textHeight(@NotNull FontFace face) {
        boolean pushed = ImGuiFonts.push(face);
        float height = ImGui.getTextLineHeight();
        ImGuiFonts.pop(pushed);
        return height;
    }

    
    
    

    
    @NotNull
    public static Optional<AtlasSpriteRef> registerSprite(@NotNull dev.technix.mica.api.MinecraftCompat compat,
                                                          @NotNull dev.technix.mica.api.SpriteBounds sprite,
                                                          @NotNull TextureFilter filter,
                                                          @NotNull dev.technix.mica.api.OverlayRenderer renderer) {
        Optional<TextureHandle> handle = renderer.registerAtlasTexture(sprite.atlasId(), filter);
        if (handle.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AtlasSpriteRef(handle.get().imGuiTextureId(),
                sprite.u0(), sprite.v0(), sprite.u1(), sprite.v1()));
    }

    
    public record AtlasSpriteRef(long textureId, float u0, float v0, float u1, float v1) {
    }
}
