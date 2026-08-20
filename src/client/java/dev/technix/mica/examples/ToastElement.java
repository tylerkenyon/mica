package dev.technix.mica.examples;

import dev.technix.mica.api.OverlayElement;
import dev.technix.mica.api.RenderContext;
import dev.technix.mica.internal.ImGuiFonts;
import dev.technix.mica.internal.util.Draw;
import imgui.ImColor;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CopyOnWriteArrayList;



public final class ToastElement implements OverlayElement {


    public enum ToastType {
        SUCCESS(0xFF0ABF30, "Success"),
        ERROR(0xFFE24D4C, "Error"),
        WARNING(0xFFE9BD0C, "Warning"),
        INFO(0xFF3498DB, "Info");


        public final int color;

        public final String label;

        ToastType(int color, String label) {
            this.color = color;
            this.label = label;
        }
    }


    private static final class Toast {
        final ToastType type;
        final String title;
        final String message;
        final float lifetimeSeconds;
        float elapsed;

        Toast(ToastType type, String title, String message, float lifetimeSeconds) {
            this.type = type;
            this.title = title;
            this.message = message;
            this.lifetimeSeconds = lifetimeSeconds;
            this.elapsed = 0f;
        }


        float slideIn() {
            return Math.min(1f, elapsed / 0.3f);
        }


        float visible() {
            return Math.min(1f, elapsed / lifetimeSeconds);
        }


        float slideOut() {
            float out = Math.max(0f, elapsed - lifetimeSeconds);
            return Math.min(1f, out / 0.3f);
        }


        boolean finished() {
            return elapsed >= lifetimeSeconds + 0.3f;
        }
    }



    private static final float TOAST_W = 360f;
    private static final float TOAST_H = 56f;
    private static final float TOAST_GAP = 8f;
    private static final float SCREEN_MARGIN = 20f;
    private static final float ROUNDING = 14f;
    private static final float PROGRESS_H = 2f;


    private static final float INSET = 18f;




    private static final int BG = ImColor.rgba(38, 42, 50, 235);

    private static final int RIM = ImColor.rgba(255, 255, 255, 26);

    private static final int TITLE = ImColor.rgba(240, 244, 250, 255);

    private static final int MESSAGE = ImColor.rgba(218, 224, 232, 178);

    private static final int PROGRESS = ImColor.rgba(255, 255, 255, 220);

    private static final int PROGRESS_TRACK = ImColor.rgba(255, 255, 255, 24);

    private final CopyOnWriteArrayList<Toast> active = new CopyOnWriteArrayList<>();
    private final Deque<Toast> pending = new ArrayDeque<>();



    private volatile boolean enabled;

    public ToastElement() {
        String override = System.getProperty("imgui.toast.enabled");
        if (override != null) {
            enabled = Boolean.parseBoolean(override);
        } else {
            enabled = FabricLoader.getInstance().isDevelopmentEnvironment();
        }
    }


    public boolean isShowing() {
        return enabled && !active.isEmpty();
    }

    public boolean isEnabled() {
        return enabled;
    }



    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            active.clear();
        }
    }



    public void enqueue(@NotNull ToastType type, @NotNull String title, @NotNull String message) {
        enqueue(type, title, message, 5.0f);
    }


    public void enqueue(@NotNull ToastType type, @NotNull String title, @NotNull String message,
                       float lifetimeSeconds) {
        if (!enabled) {
            return;
        }
        pending.addLast(new Toast(type, title, message, Math.max(0.5f, lifetimeSeconds)));
    }

    @Override
    @NotNull
    public String name() {
        return "ToastElement";
    }

    @Override
    public boolean isVisible(@NotNull RenderContext context) {


        return enabled;
    }

    @Override
    public void render(@NotNull RenderContext context) {
        if (!pending.isEmpty()) {
            Toast head;
            while ((head = pending.pollFirst()) != null) {
                active.add(head);
            }
        }


        if (!enabled) {
            active.clear();
            return;
        }

        float delta = Math.min(0.1f, context.deltaTime());



        float screenW = context.width();
        float screenH = context.height();



        float horizontalRoom = Math.max(0f, screenW - TOAST_W);
        float verticalRoom = Math.max(0f, screenH - TOAST_H);
        float marginX = Math.min(SCREEN_MARGIN, horizontalRoom / 2f);
        float marginY = Math.min(SCREEN_MARGIN, verticalRoom / 2f);
        float restingX = Math.max(0f, marginX);
        float restingY = screenH - TOAST_H - marginY;

        int n = active.size();
        for (int idx = 0; idx < n; idx++) {
            Toast toast = active.get(idx);
            toast.elapsed += delta;


            float slideIn = toast.slideIn();
            float slideOut = toast.slideOut();
            float slideOffset;
            if (slideIn < 1f) {
                float overshoot = slideIn < 0.4f
                        ? 0f
                        : (slideIn < 0.8f
                                ? 0.05f * TOAST_W * (slideIn - 0.4f) / 0.4f
                                : (0.05f * TOAST_W - (slideIn - 0.8f) / 0.2f * (TOAST_W * 0.05f - 10f)));
                slideOffset = (slideIn - 1f) * TOAST_W + overshoot;
            } else if (slideOut > 0f) {
                slideOffset = slideOut * (-TOAST_W - 20f) + 10f;
            } else {
                slideOffset = 10f;
            }

            float x = restingX + slideOffset;


            float y = restingY - (n - 1 - idx) * (TOAST_H + TOAST_GAP);

            drawToast(context, toast, x, y);
        }

        active.removeIf(Toast::finished);
    }



    private void drawToast(@NotNull RenderContext context, @NotNull Toast toast, float x, float y) {

        Draw.roundedRect(context, x, y, TOAST_W, TOAST_H, ROUNDING, BG);
        Draw.roundedRectOutline(context, x, y, TOAST_W, TOAST_H, ROUNDING, RIM, 1.0f);

        float contentX = x + INSET;
        float titleY = y + 10f;
        Draw.text(context, ImGuiFonts.bold(), TITLE, "[DEV] " + toast.title, contentX, titleY);

        float messageY = titleY + Draw.textHeight(ImGuiFonts.bold()) + 2f;
        Draw.text(context, ImGuiFonts.regular(), MESSAGE, toast.message, contentX, messageY);

        float progress = 1f - toast.visible();
        float barY = y + TOAST_H - PROGRESS_H;
        imgui.ImDrawList drawList = context.drawList();
        drawList.pushClipRect(x + ROUNDING, barY,
                x + TOAST_W - ROUNDING, y + TOAST_H, true);
        Draw.progressBar(context, x, barY, TOAST_W, PROGRESS_H, progress,
                PROGRESS_TRACK, PROGRESS);
        drawList.popClipRect();
    }
}
