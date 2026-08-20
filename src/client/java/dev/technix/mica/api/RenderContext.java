package dev.technix.mica.api;

import imgui.ImDrawList;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;


public record RenderContext(
        @NotNull ImDrawList drawList,
        float width,
        float height,
        long blurTextureId,
        float deltaTime,
        @NotNull FrostedGlassStyle glassStyle,
        @NotNull OverlayRenderer renderer) {

    
    public boolean hasBlur() {
        return blurTextureId != 0L;
    }

    
    public boolean inGame() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.level != null && minecraft.player != null;
    }
}
