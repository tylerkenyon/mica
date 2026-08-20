package dev.technix.mica.mixin.client;

import dev.technix.mica.internal.ActiveRenderers;
import net.minecraft.client.gui.render.GuiRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {

    private static final Logger IMGUI_LOGGER = LoggerFactory.getLogger("mica");

    private static final boolean IMGUI_SKIP_BLUR =
            Boolean.getBoolean("imgui.debug.skipBlur");
    private static final boolean IMGUI_SKIP_DRAW =
            Boolean.getBoolean("imgui.debug.skipDraw");

    private static boolean imgui$failed;

    @Inject(method = "render()V", at = @At("HEAD"))
    private void imgui$prepareFrame(CallbackInfo callbackInfo) {
        if (imgui$failed || IMGUI_SKIP_DRAW) {
            return;
        }
        try {
            ActiveRenderers.prepareForFrame();
        } catch (Throwable throwable) {
            imgui$reportFailure("preparing the ImGui frame", throwable);
        }
    }

    @Inject(method = "render()V", at = @At("TAIL"))
    private void imgui$recordOverlay(CallbackInfo callbackInfo) {
        if (imgui$failed || IMGUI_SKIP_DRAW) {
            return;
        }
        try {
            ActiveRenderers.renderOverlay();
        } catch (Throwable throwable) {
            imgui$reportFailure("recording the ImGui overlay", throwable);
        }
    }

    private static void imgui$reportFailure(String what, Throwable throwable) {
        imgui$failed = true;
        IMGUI_LOGGER.error("Disabling the ImGui overlay after a failure while {}", what, throwable);
    }
}
