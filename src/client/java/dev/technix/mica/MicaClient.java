package dev.technix.mica;

import dev.technix.mica.api.FontRegistry;
import dev.technix.mica.api.FrostedGlassStyle;
import dev.technix.mica.api.OverlayRenderer;
import dev.technix.mica.api.compat.v26_2.MinecraftCompatImpl_26_2;
import dev.technix.mica.examples.ToastElement;
import dev.technix.mica.internal.ActiveRenderers;
import imgui.ImColor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MicaClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("mica");

    private static final FrostedGlassStyle SLEEK = FrostedGlassStyle.builder()
            .blurScaleDivisor(2)
            .blurPasses(6)
            .defaultTint(ImColor.rgba(38, 42, 50, 235))
            .defaultBorder(ImColor.rgba(255, 255, 255, 32))
            .defaultRounding(14f)
            .build();

    @Override
    public void onInitializeClient() {
        FontRegistry exampleFonts = new FontRegistry(
                Identifier.fromNamespaceAndPath("mica", "font"));

        OverlayRenderer renderer = OverlayRenderer.builder()
                .withMinecraftCompat(new MinecraftCompatImpl_26_2())
                .withFrostedGlass(true)
                .withFrostedGlassStyle(SLEEK)
                .withFontRegistry(exampleFonts)
                .build();

        ToastElement toasts = new ToastElement();
        renderer.registerElement(toasts);

        ActiveRenderers.set(renderer);

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            exampleFonts.add("body-lg", "sf-pro-display-medium.otf", 18.0f);
            exampleFonts.commitPendingFaces();
            toasts.enqueue(ToastElement.ToastType.SUCCESS,
                    "mica",
                    "Platform initialised - Minecraft 26.2 adapter wired in.");
            LOGGER.info("mica platform initialised (Minecraft 26.2 adapter wired in).");
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ActiveRenderers.set(null);
            renderer.close();
            LOGGER.info("mica platform shut down.");
        });
    }
}
