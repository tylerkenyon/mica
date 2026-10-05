package dev.technix.mica.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.technix.mica.internal.ActiveRenderers;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


// The cursor position is not hooked here: Minecraft's onMove signature differs between
// versions (26.3 added relative deltas), so the renderer reads MouseHandler.xpos()/ypos()
// once per frame through MinecraftCompat.cursorPosition() instead.
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {


    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V",
            at = @At("HEAD"), cancellable = true)
    private void imguiOnButton(long windowPointer, MouseButtonInfo buttonInfo, int action,
                               CallbackInfo ci) {
        boolean pressed = action != InputConstants.RELEASE;
        ActiveRenderers.feedMouseButton(buttonInfo.button(), pressed);
        if (ActiveRenderers.wantsMouse()) {
            ci.cancel();
        }
    }


    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void imguiOnScroll(long windowPointer, double xOffset, double yOffset,
                               CallbackInfo ci) {
        ActiveRenderers.feedMouseWheel(xOffset, yOffset);
        if (ActiveRenderers.wantsMouse()) {
            ci.cancel();
        }
    }
}
