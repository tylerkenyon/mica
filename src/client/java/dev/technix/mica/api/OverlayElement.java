package dev.technix.mica.api;

import org.jetbrains.annotations.NotNull;



public interface OverlayElement {


    @NotNull
    String name();

    default boolean isVisible(@NotNull RenderContext context) {
        return true;
    }


    @NotNull
    default MicaScreen renderScope() {
        return MicaScreen.ANY;
    }

    void render(@NotNull RenderContext context);
}
