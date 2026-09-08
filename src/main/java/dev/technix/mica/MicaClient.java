package dev.technix.mica;

import dev.technix.mica.api.FontRegistry;
import dev.technix.mica.api.FrostedGlassStyle;
import dev.technix.mica.api.OverlayRenderer;
import dev.technix.mica.api.compat.v26_2.MinecraftCompatImpl_26_2;
import dev.technix.mica.examples.ToastElement;
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
        // Mica is a library; MicaClient is only a standalone demo overlay when running the Mica project directly.
        // If a consumer mod (such as Vanish) or an active OverlayRenderer is detected, skip the demo overlay.
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("vanish")
                || dev.technix.mica.internal.ActiveRenderers.get() != null) {
            LOGGER.info("Consumer overlay detected; skipping standalone Mica demo client.");
            return;
        }

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

        renderer.makeActive();

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            // Any face name the platform font can be asked for; there is no bundled file to name.
            exampleFonts.add("body-lg", "system-ui.ttf", 18.0f);
            exampleFonts.commitPendingFaces();
            toasts.enqueue(ToastElement.ToastType.SUCCESS,
                    "mica",
                    "Platform initialised - Minecraft 26.2 adapter wired in.");
            LOGGER.info("mica platform initialised (Minecraft 26.2 adapter wired in).");
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            renderer.close();
            LOGGER.info("mica platform shut down.");
        });
    }

}
