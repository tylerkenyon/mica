package dev.technix.mica.mixin.client;

import dev.technix.mica.internal.ActiveRenderers;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;



@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V",
            at = @At("HEAD"), cancellable = true)
    private void imguiKeyPress(long windowPointer, int action, KeyEvent event,
                               CallbackInfo ci) {
        ActiveRenderers.feedKey(event.key(), event.scancode(), action);
        if (ActiveRenderers.wantsKeyboard()) {
            ci.cancel();
        }
    }

    @Inject(method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V",
            at = @At("HEAD"), cancellable = true)
    private void imguiCharTyped(long windowPointer, CharacterEvent event,
                                CallbackInfo ci) {
        ActiveRenderers.feedChar(event.codepoint());
        if (ActiveRenderers.wantsKeyboard()) {
            ci.cancel();
        }
    }
}
