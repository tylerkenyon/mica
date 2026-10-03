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

    /**
     * Draw order within a frame. Higher draws later, and therefore on top.
     *
     * <p>Every element strokes into the same background draw list, so without this the only thing
     * deciding what covers what is the order elements happened to be registered in - which lives in
     * bootstrap code, far away from the elements that care about it. An element that must sit above
     * the rest says so here instead.
     */
    default int layer() {
        return 0;
    }

    void render(@NotNull RenderContext context);
}
