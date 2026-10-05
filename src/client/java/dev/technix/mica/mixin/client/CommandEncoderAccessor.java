package dev.technix.mica.mixin.client;

//? if >=26.3 {
/*import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
*///?} else {
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;


// On 26.3 FrontendCommandEncoder.backend() is public and the adapter calls it directly; the
// accessor stays so both versions apply the same mixin config.
//? if >=26.3 {
/*@Mixin(FrontendCommandEncoder.class)
*///?} else {
@Mixin(CommandEncoder.class)
//?}
public interface CommandEncoderAccessor {

    @Invoker("backend")
    CommandEncoderBackend imgui$backend();
}
