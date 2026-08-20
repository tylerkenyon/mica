# Public API Reference

The Mica API is divided into three groups of packages:

* `dev.technix.mica.api.*` — the public surface. Consumers import from here.
* `dev.technix.mica.api.compat.*` — version adapters (currently only `v26_2`).
* `dev.technix.mica.internal.*` — the renderer, Vulkan backend, screen detector,
  input router. Public by Java visibility, conceptually private.

Anything that promises more than "register HUD, draw HUD, optionally enable frosted
glass" sits in `compat` or `internal`. The boundary is enforced socially — PRs
cross-package-importing from `internal.*` into `api.*` should be rejected.

---

## `dev.technix.mica.api.OverlayRenderer`

The main entrypoint. One instance owns the entire render loop, runs on the render
thread via `GuiRendererMixin`, and exposes the rest of the API to user elements.

### Builder

```java
OverlayRenderer.Builder<...
OverlayRenderer.builder()                      // static builder()
        .withMinecraftCompat(MinecraftCompat) // Required.
        .withFrostedGlass(boolean)             // Optional. Default true.
        .withFrostedGlassStyle(FrostedGlassStyle) // Optional. Default DEFAULT.
        .withFontRegistry(FontRegistry)       // Optional. May chain.
        .withFontRegistries(List<FontRegistry>) // Optional. May chain.
        .build();
```

`withFrostedGlassStyle(sleek)` accepts any value returned by
`FrostedGlassStyle.builder().build()`. The default is `FrostedGlassStyle.DEFAULT`
(half-res divisor 2, five passes, `Palette.PANEL`/`Palette.BORDER`, 8 px rounding).

### Lifecycle methods

| Method                                                                              | Purpose                                                                                          |
| ----------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `prepareForFrame()`                                                                 | No-op when Vulkan is not active. Fast-path on the render thread ahead of host GUI submissions.   |
| `renderOverlay()`                                                                   | No-op when Vulkan is not active. Drives every registered element through one frame of work, then submits into the host `VkCommandBuffer`. |
| `close()`                                                                           | Releases every owned GPU resource (samplers, descriptor sets, font atlas upload, blur targets).  |
| `registerElement(OverlayElement)` / `unregisterElement(OverlayElement)`             | Add / remove an element from the per-frame draw queue. Registration order is draw order (back to front). |
| `elements()`                                                                        | Snapshot of currently-registered elements.                                                       |

### Texture + sprite helpers

| Method                                                  | Purpose                                                                                          |
| ------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `registerAtlasTexture(Identifier, TextureFilter)`      | Returns an ImGui texture handle bound to a Minecraft atlas identifier (e.g. `minecraft:items`). Re-resolves if Mojang rebuilds the image. |
| `minecrftCompat()`                                       | Reach the version adapter from inside an element. Convenience for advanced authors.              |

### Customisation accessors

| Method                                | Purpose                                                                                          |
| ------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `font(String name)`                   | Look up a rasterised font face by name (bundled or user-registered). Returns `null` if absent.   |
| `glassStyle()`                        | The current frosted-glass style.                                                                  |
| `setGlassStyle(FrostedGlassStyle)`    | Swap the style at runtime; pass count takes effect immediately, divisor triggers a target re-allocation on next frame. |
| `palette()`                           | The platform `Palette` for default colours.                                                       |
| `fonts()`                             | Object-style facade over the platform's font helpers (`push(face)` / `pop(pushed)` / `regular()` / `medium()` / `bold()` / `logo()`). |

### Static helpers

| Method                                                 | Purpose                                                                  |
| ------------------------------------------------------ | ------------------------------------------------------------------------ |
| `pushFont(FontFace)` / `popFont(boolean)`              | Re-entrant font binding for callers that want object-style access.       |
| `text(ImDrawList, FontFace, int color, String, float, float)` | One-call text-with-face.                                           |
| `imGuiNewFrame(int w, int h, float deltaTime)`          | Caller-driven `ImGui.newFrame()` if you want to drive the frame yourself. |

---

## `dev.technix.mica.api.OverlayElement`

```java
public interface OverlayElement {
    String name();
    default boolean isVisible(RenderContext context) { return true; }
    default MicaScreen renderScope() { return MicaScreen.ANY; }
    void render(RenderContext context);
}
```

See [`elements.md`](./elements.md) for the per-frame lifecycle and a worked example.

A throwing element is disabled for the rest of the session — one broken HUD cannot
take the rest of the overlay with it.

---

## `dev.technix.mica.api.RenderContext`

A record passed to every `render(...)` call.

| Field            | Type            | Meaning                                                                                  |
| ---------------- | --------------- | ---------------------------------------------------------------------------------------- |
| `drawList`       | `ImDrawList`    | The Dear ImGui draw list (currently always the background list).                         |
| `width`          | `float`         | Framebuffer width in pixels.                                                              |
| `height`         | `float`         | Framebuffer height in pixels.                                                            |
| `blurTextureId`  | `long`          | ImGui texture ID for the pre-blurred screen, or `0` if the blur hasn't been done.        |
| `deltaTime`      | `float`         | Frame delta in seconds, clamped so a stalled HUD doesn't skip animation cycles.           |
| `glassStyle`     | `FrostedGlassStyle` | The user-installed style; read by `Draw.frostedPanel(context, x, y, w, h)`.            |
| `renderer`       | `OverlayRenderer` | The owning renderer. Reach the version adapter or texture helpers through it.            |

`hasBlur()` returns `blurTextureId != 0L`. `inGame()` is `Minecraft.getInstance().level
!= null && Minecraft.getInstance().player != null`.

---

## `dev.technix.mica.api.MicaScreen`

```java
public enum MicaScreen { ANY, TITLE, IN_GAME_HUD, PAUSE, INVENTORY, CHAT, OTHER }
```

The enum `OverlayElement.renderScope()` returns. See [`contexts.md`](./contexts.md)
for the detector's per-frame work.

---

## `dev.technix.mica.api.MinecraftCompat`

The Mojang-version-shaped bridge. The interface lives here; the implementation lives
in `dev.technix.mica.api.compat.v26_2.MinecraftCompatImpl_26_2`. The builder refuses
to build without a non-null `MinecraftCompat`.

| Method                                              | Purpose                                                                            |
| --------------------------------------------------- | ---------------------------------------------------------------------------------- |
| `Optional<VulkanContext> currentVulkanContext()`   | The host's current Vulkan framebuffer state. Empty when not rendering with Vulkan. |
| `boolean isVulkanRendererActive()`                  | Hot-path guard, used by `OverlayRenderer.prepareForFrame()`.                        |
| `Optional<SpriteBounds> locateItemIcon(ItemStack)` | UV bounds for an item sprite in the items atlas.                                  |
| `long vkImageViewFor(Identifier)`                   | The bridge to a Minecraft texture's `vkImageView` handle.                          |
| `Optional<SpriteBounds> locateSprite(NamespaceID, String name)` | UV bounds for an arbitrary sprite in a host atlas.                      |

The Vulkan-specific ones are only reachable from inside the platform; consumers who
want to bridge to other Mojang versions write their own adapter against this
interface.

---

## `dev.technix.mica.api.Palette`

A flat colour palette of packed `ImColor.rgba(...)` ints. Stable identifiers the
platform ships:

| Constant       | Approximate value                          |
| -------------- | ----------------------------------------- |
| `PANEL`        | The frosted-glass base tint.              |
| `BORDER`       | The frosted-glass hairline rim.            |
| `TEXT`         | Body text.                                |
| `SLOT`         | Inventory slot tint.                       |
| `BACKDROP_TINT`| Tinted dimming for non-glass overlays.    |
| `DEV_ONLY`     | The dev-only marker colour for toasts.    |

Override by defining your own constants targeting the same use cases.

---

## `dev.technix.mica.api.FontRegistry` & `dev.technix.mica.api.FontFace`

```java
public final class FontRegistry {
    public FontRegistry(Identifier root);                // e.g. mymod:fonts
    public @Nullable FontFace add(String name, String fileName, float sizePixels);
    public @Nullable FontFace get(String name);
    public @NotNull  Collection<FontFace> all();
}

public record FontFace(@NotNull String name, int pixelSize, @Nullable ImFont imFont) {
    public static final String REGULAR = "regular";
    public static final String MEDIUM = "medium";
    public static final String BOLD = "bold";
    public static final String LOGO = "logo";
}
```

See [`customisation.md`](./customisation.md) for end-to-end usage.

---

## `dev.technix.mica.api.FrostedGlassStyle`

```java
public record FrostedGlassStyle(
        int blurScaleDivisor,      // ≥ 2
        int blurPasses,            // [1, 16]
        int defaultTint,           // ImColor.rgba(...)
        int defaultBorder,         // 0 to suppress
        float defaultRounding) {  // ≥ 0

    public static FrostedGlassStyle DEFAULT;

    public static Builder builder();
}
```

The builder validates on `build()`. Pass count clamps at 16 because the descriptor-set
ring has 16 slots in the current pipeline.

---

## `dev.technix.mica.api.SpriteBounds`, `TextureFilter`, `TextureHandle`

```java
public record SpriteBounds(
        @NotNull Identifier atlasId,
        float u0, float v0, float u1, float v1);

public enum TextureFilter { NEAREST, LINEAR }

public record TextureHandle(long imGuiTextureId, TextureFilter filter);
```

A `SpriteBounds` is the UV rect of a sprite inside a Minecraft atlas. A `TextureHandle`
is the cache façade that survives Mojang's atlas-image rebuild; tags are re-resolved
every check point.

---

## `dev.technix.mica.api.VanillaAtlases`

```java
public final class VanillaAtlases {
    public static final Identifier ITEMS    = ...;
    public static final Identifier BLOCKS   = ...;
    public static final Identifier GUI      = ...;
}
```

Identifier constants the consumer side imports when looking up vanilla sprites.
Future Minecraft releases can add to this list without breaking callers.

---

## `dev.technix.mica.examples.ToastElement`

A reference `OverlayElement`. Reads `MicaScreen.ANY`, queues dev-only toasts,
animates in from the left, scissor-clips the bottom progress bar to the panel
interior so it doesn't poke out of the rounded silhouette.

Use it as a worked example when writing your own element. The slim jar ships it
verbatim; copying is encouraged.

---

## The internal surface (for advanced authors)

`dev.technix.mica.internal.*` is the implementation. Public-by-Java but conceptually
private: you can read it (its methods are not package-private) but it is not part of
the contract. Future versions will rewrite internals freely.

For someone extending the platform — writing a `MinecraftCompatImpl_v26_3` for a
mining Minecraft point release, or a new compute kernel for `FrostedGlassRenderer`,
or a different screen detector — [`internals.md`](./internals.md) is the right
starting point.

## Reading more

* [`elements.md`](./elements.md) — `OverlayElement` lifecycle and example.
* [`customisation.md`](./customisation.md) — `FontRegistry` and `FrostedGlassStyle`.
* [`vulkan.md`](./vulkan.md) — the 26.2-specific Vulkan pipeline.
* [`internals.md`](./internals.md) — what each `internal` class does.
