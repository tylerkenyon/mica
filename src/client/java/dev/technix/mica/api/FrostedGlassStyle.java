package dev.technix.mica.api;

import org.jetbrains.annotations.NotNull;



public record FrostedGlassStyle(
        int blurScaleDivisor,
        int blurPasses,
        int defaultTint,
        int defaultBorder,
        float defaultRounding) {


    @NotNull
    public static final FrostedGlassStyle DEFAULT = FrostedGlassStyle.builder()
            .blurScaleDivisor(2)
            .blurPasses(5)
            .defaultTint(Palette.PANEL)
            .defaultBorder(Palette.BORDER)
            .defaultRounding(8f)
            .build();



    @NotNull
    public static Builder builder() {
        return new Builder();
    }


    public static final class Builder {
        private int blurScaleDivisor = 2;
        private int blurPasses = 5;
        private int defaultTint = Palette.PANEL;
        private int defaultBorder = Palette.BORDER;
        private float defaultRounding = 8f;

        private Builder() {
        }

        @NotNull
        public Builder blurScaleDivisor(int divisor) {
            this.blurScaleDivisor = divisor;
            return this;
        }

        @NotNull
        public Builder blurPasses(int passes) {
            this.blurPasses = passes;
            return this;
        }

        @NotNull
        public Builder defaultTint(int tint) {
            this.defaultTint = tint;
            return this;
        }



        @NotNull
        public Builder defaultBorder(int border) {
            this.defaultBorder = border;
            return this;
        }


        @NotNull
        public Builder defaultRounding(float rounding) {
            this.defaultRounding = rounding;
            return this;
        }



        @NotNull
        public FrostedGlassStyle build() {
            if (blurScaleDivisor < 2) {
                throw new IllegalArgumentException(
                        "blurScaleDivisor must be at least 2; got " + blurScaleDivisor);
            }
            if (blurPasses < 1 || blurPasses > 16) {
                throw new IllegalArgumentException(
                        "blurPasses must be in [1, 16]; got " + blurPasses);
            }
            if (defaultRounding < 0f) {
                throw new IllegalArgumentException(
                        "defaultRounding must be non-negative; got " + defaultRounding);
            }
            return new FrostedGlassStyle(blurScaleDivisor, blurPasses,
                    defaultTint, defaultBorder, defaultRounding);
        }
    }
}
