package dev.technix.mica.internal;

import dev.technix.mica.api.MicaScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.jetbrains.annotations.NotNull;



public final class ScreenDetector {

    private static final MicaScreen[] CACHE = new MicaScreen[16];

    private ScreenDetector() {
    }

    @NotNull
    public static MicaScreen current() {
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
