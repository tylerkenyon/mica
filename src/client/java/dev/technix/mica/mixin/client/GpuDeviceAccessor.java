package dev.technix.mica.mixin.client;

//? if >=26.3 {
/*import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;
*///?} else {
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;



//? if >=26.3 {
/*@Mixin(FrontendGpuDevice.class)
*///?} else {
@Mixin(GpuDevice.class)
//?}
public interface GpuDeviceAccessor {

    @Accessor("backend")
    GpuDeviceBackend imgui$backend();
}
