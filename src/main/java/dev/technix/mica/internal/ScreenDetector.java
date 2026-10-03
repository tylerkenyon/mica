package dev.technix.mica.internal;

import dev.technix.mica.api.MicaScreen;
import imgui.ImGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.jetbrains.annotations.NotNull;


/**
 * Maps the currently open Minecraft {@link net.minecraft.client.gui.screens.Screen}
 * to a {@link MicaScreen} scope. The result is cached per ImGui frame so that
 * evaluating once per registered element costs nothing beyond the first call.
 */
public final class ScreenDetector {

    /** Per-frame cached result. */
    private static MicaScreen cachedResult = MicaScreen.ANY;
    /** The ImGui frame count on which {@link #cachedResult} was last evaluated. */
    private static int cachedFrame = -1;

    private ScreenDetector() {
    }

    @NotNull
    public static MicaScreen current() {
        int frame = ImGui.getFrameCount();
        if (frame == cachedFrame) {
            return cachedResult;
        }
        cachedFrame = frame;
        cachedResult = evaluate();
        return cachedResult;
    }

    @NotNull
    private static MicaScreen evaluate() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            return MicaScreen.ANY;
        }

        net.minecraft.client.gui.screens.Screen screen = minecraft.gui.screen();
        if (screen == null) {
            return MicaScreen.IN_GAME_HUD;
        }
        if (screen instanceof TitleScreen) {
            return MicaScreen.TITLE;
        }
        if (screen instanceof PauseScreen) {
            return MicaScreen.PAUSE;
        }
        if (screen instanceof ChatScreen) {
            return MicaScreen.CHAT;
        }
        if (screen instanceof AbstractContainerScreen<?>) {
            return MicaScreen.INVENTORY;
        }
        return MicaScreen.OTHER;
    }
}
