package dev.technix.mica.internal;

import dev.technix.mica.api.FontRegistry;
import dev.technix.mica.api.FrostedGlassStyle;
import dev.technix.mica.api.OverlayRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;


public final class ActiveRenderers {

    private static final AtomicReference<OverlayRenderer> ACTIVE = new AtomicReference<>();

    
    private static final List<FontRegistry> PENDING_REGISTRIES = new ArrayList<>();

    
    private static volatile FrostedGlassStyle PENDING_STYLE = FrostedGlassStyle.DEFAULT;

    private ActiveRenderers() {
    }

    public static void set(@Nullable OverlayRenderer renderer) {
        ACTIVE.set(renderer);
    }

    public static @Nullable OverlayRenderer get() {
        return ACTIVE.get();
    }

    
    public static void addFontRegistry(@NotNull FontRegistry registry) {
        synchronized (PENDING_REGISTRIES) {
            PENDING_REGISTRIES.add(registry);
        }
    }

    
    public static void setFrostedGlassStyle(@NotNull FrostedGlassStyle style) {
        PENDING_STYLE = style;
    }

    @NotNull
    public static List<FontRegistry> fontRegistries() {
        synchronized (PENDING_REGISTRIES) {
            return List.copyOf(PENDING_REGISTRIES);
        }
    }

    @NotNull
    public static FrostedGlassStyle frostedGlassStyle() {
        return PENDING_STYLE;
    }

    
    public static void consumePending() {
        synchronized (PENDING_REGISTRIES) {
            PENDING_REGISTRIES.clear();
        }
        
        
        PENDING_STYLE = FrostedGlassStyle.DEFAULT;
    }

    public static void feedMouseMove(double x, double y) {
        ImGuiInputRouter.onMouseMove(imGuiRenderer(), x, y);
    }

    public static void feedMouseButton(int button, boolean pressed) {
        ImGuiInputRouter.onMouseButton(imGuiRenderer(), button, pressed);
    }

    public static void feedMouseWheel(double xOffset, double yOffset) {
        ImGuiInputRouter.onMouseScroll(imGuiRenderer(), xOffset, yOffset);
    }

    public static void feedKey(int key, int scancode, int action) {
        ImGuiInputRouter.onKey(imGuiRenderer(), key, scancode, action);
    }

    public static void feedChar(int codepoint) {
        ImGuiInputRouter.onChar(imGuiRenderer(), codepoint);
    }

    public static boolean wantsMouse() {
        ImGuiRenderer r = imGuiRenderer();
        return r != null && r.wantsToCaptureMouse();
    }

    public static boolean wantsKeyboard() {
        ImGuiRenderer r = imGuiRenderer();
        return r != null && r.wantsToCaptureKeyboard();
    }

    public static boolean prepareForFrame() {
        OverlayRenderer r = ACTIVE.get();
        return r != null && r.prepareForFrame();
    }

    public static void renderOverlay() {
        OverlayRenderer r = ACTIVE.get();
        if (r != null) {
            r.renderOverlay();
        }
    }

    private static @Nullable ImGuiRenderer imGuiRenderer() {
        OverlayRenderer r = ACTIVE.get();
        return r == null ? null : r.internalRenderer();
    }
}
