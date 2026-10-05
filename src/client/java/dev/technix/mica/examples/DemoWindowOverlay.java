package dev.technix.mica.examples;

import dev.technix.mica.api.Mica;
import dev.technix.mica.api.MicaTexture;
import dev.technix.mica.api.OverlayElement;
import dev.technix.mica.api.RenderBackendType;
import dev.technix.mica.api.RenderContext;
import dev.technix.mica.api.TextureFilter;
import dev.technix.mica.api.VanillaAtlases;
import dev.technix.mica.internal.util.Draw;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import org.jetbrains.annotations.NotNull;


/**
 * A plain Dear ImGui window exercising text, buttons, sliders, text input, a texture and a
 * frosted panel. Nothing in it knows which backend Minecraft runs; use it to check a
 * Vulkan run and an OpenGL run behave the same. Enabled in the dev client with
 * {@code -Dmica.demoWindow=true} ({@code -PmicaDemoWindow} on {@code runClient}).
 */
public final class DemoWindowOverlay implements OverlayElement {

    private final Mica mica;
    private final MicaTexture items;
    private final float[] slider = {0.5f};
    private final ImString input = new ImString("Type here", 128);
    private final ImBoolean showPanel = new ImBoolean(true);
    private int clicks;

    public DemoWindowOverlay(@NotNull Mica mica) {
        this.mica = mica;
        this.items = mica.texture(VanillaAtlases.ITEMS, TextureFilter.NEAREST);
    }

    @Override
    public @NotNull String name() {
        return "mica-demo-window";
    }

    @Override
    public void render(@NotNull RenderContext context) {
        if (showPanel.get()) {
            Draw.frostedPanel(context, context.width() - 260f, 20f, 240f, 80f);
        }

        ImGui.setNextWindowPos(40f, 40f, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(360f, 320f, ImGuiCond.FirstUseEver);
        if (ImGui.begin("Mica demo")) {
            String backend = mica.backend().map(RenderBackendType::displayName).orElse("detecting");
            ImGui.text("Hello Minecraft! Backend: " + backend);
            ImGui.text(String.format("Viewport %.0fx%.0f, %.1f ms", context.width(),
                    context.height(), context.deltaTime() * 1000f));
            ImGui.separator();
            if (ImGui.button("Click me")) {
                clicks++;
            }
            ImGui.sameLine();
            ImGui.text("clicked " + clicks + "x");
            ImGui.sliderFloat("Slider", slider, 0f, 1f);
            ImGui.inputText("Text", input);
            ImGui.checkbox("Frosted panel", showPanel);
            ImGui.text(context.hasBlur() ? "Backdrop blur: on" : "Backdrop blur: off");
            long textureId = items.imGuiTextureId();
            if (textureId != 0L) {
                ImGui.text("Items atlas:");
                ImGui.image(textureId, 128f, 128f);
            } else {
                ImGui.text("Items atlas not available yet");
            }
        }
        ImGui.end();
    }
}
