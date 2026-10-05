# Public API Reference

The Mica API is divided into three groups of packages:

* `dev.technix.mica.api.*` — the public surface. Consumers import from here.
* `dev.technix.mica.api.compat.*` — version adapters (currently only `v26_2`).
* `dev.technix.mica.internal.*` — the renderer core, the Vulkan and OpenGL backends,
  screen detector, input router. Public by Java visibility, conceptually private.

Nothing in `dev.technix.mica.api` (outside `compat`) exposes a Vulkan, OpenGL, GLFW
or Mojang rendering type. The same calls work on both of Minecraft 26.2's backends;
see [`backends.md`](./backends.md).

Anything that promises more than "register HUD, draw HUD, optionally enable frosted
glass" sits in `compat` or `internal`. The boundary is enforced socially — PRs
cross-package-importing from `internal.*` into `api.*` should be rejected.

---

## `dev.technix.mica.api.Mica`

The entry point most mods need.

```java
Mica mica = Mica.create();                       // defaults; installs + auto-closes
Mica mica = Mica.builder()
        .frostedGlass(true)                      // default true
        .frostedGlassStyle(FrostedGlassStyle)    // default DEFAULT
        .fontRegistry(FontRegistry)              // may chain
        .minecraftCompat(MinecraftCompat)        // rarely needed; auto-detected
        .closeOnClientStop(boolean)              // default true (Fabric CLIENT_STOPPING)
        .build();
```

| Method                                             | Purpose |
| -------------------------------------------------- | ------- |
| `registerOverlay(MicaOverlay)` / `registerOverlay(String, MicaOverlay)` | Lambda overlay drawn every frame on every screen. Returns the wrapping `OverlayElement`. |
| `registerOverlay(T extends OverlayElement)`        | Full element (screen scope, visibility). |
| `unregisterOverlay(OverlayElement)`                | Remove an overlay. Any thread. |
| `texture(Identifier, TextureFilter)`               | A `MicaTexture` for a Minecraft texture or atlas. |
| `backend()`                                        | `Optional<RenderBackendType>`, for diagnostics only. |
| `renderer()`                                       | The underlying `OverlayRenderer` (fonts, glass style, palette). |
| `close()`                                          | Uninstall and release everything (render thread). |

`MicaOverlay` is `@FunctionalInterface void render(RenderContext)`.

---

## `dev.technix.mica.api.MicaTexture`

```java
public interface MicaTexture extends AutoCloseable {
    long imGuiTextureId();     // Dear ImGui ImTextureID for this frame; 0 if unavailable
    default boolean isValid(); // imGuiTextureId() != 0
    void close();              // idempotent, any thread
}
```

Opaque and backend-independent: pass the id to `ImGui.image`, `ImDrawList.addImage`
or `Draw.image`. Owned by the renderer; follows Minecraft re-creating the texture;
handles for the same texture + filter share one GPU registration; the last `close()`
releases it once no frame in flight can still sample it. Full lifetime rules are in
[`backends.md`](./backends.md#textures).

---

## `dev.technix.mica.api.RenderBackendType` and `MicaBackendException`

`enum RenderBackendType { VULKAN, OPENGL, UNKNOWN }`, informational only.
`MicaBackendException` (a `RuntimeException` with `backend()`) is what
`OverlayRenderer.failure()` reports when no renderer could start. Its message names the
Minecraft version, detected backend, Mica version and reason. It is logged, never
thrown into Minecraft.

---

## `dev.technix.mica.api.OverlayRenderer`

The main entrypoint. One instance owns the entire render loop, runs on the render
thread via `GuiRendererMixin`, and exposes the rest of the API to user elements.

### Builder

```java
OverlayRenderer.Builder<...
OverlayRenderer.builder()                      // static builder()
        .withMinecraftCompat(MinecraftCompat) // Optional. Default MinecraftCompat.detect().
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
| `prepareForFrame()`                                                                 | Render thread, ahead of host GUI submissions. Detects the backend on first use, starts the ImGui frame. Returns `false` (no-op) until a renderer is ready. |
| `renderOverlay()`                                                                   | Drives every registered element through one frame of work, then hands the draw data to the active backend. No-op if `prepareForFrame()` did not start a frame. |
| `close()`                                                                           | Releases every owned GPU resource (font atlas, pipelines/programs, texture registrations, blur targets) and the ImGui context. |
| `activeBackend()` / `failure()`                                                     | The detected `RenderBackendType`; the `MicaBackendException` if no renderer could start.         |
| `registerElement(OverlayElement)` / `unregisterElement(OverlayElement)`             | Add / remove an element from the per-frame draw queue. Registration order is draw order (back to front). |
| `elements()`                                                                        | Snapshot of currently-registered elements.                                                       |

### Texture + sprite helpers

| Method                                                  | Purpose                                                                                          |
| ------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `texture(Identifier, TextureFilter)`                    | A long-lived `MicaTexture` for a Minecraft texture or atlas. Works on every backend. |
| `registerAtlasTexture(Identifier, TextureFilter)`      | The current ImGui texture id for an atlas as a per-frame `TextureHandle` snapshot (cached per atlas + filter). Re-resolves if Mojang rebuilds the image. |
| `registerRawTexture(long, int, TextureFilter)`         | **Deprecated.** Raw Vulkan image view + layout; Vulkan only, returns `0` elsewhere. |
| `minecraftCompat()`                                     | Reach the version adapter from inside an element. Convenience for advanced authors.              |

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
| `blurTextureId`  | `long`          | ImGui texture ID for the pre-blurred screen (either backend), or `0` if there is no blur this frame. |
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
in `dev.technix.mica.api.compat.v26_2.MinecraftCompatImpl_26_2`.
`MinecraftCompat.detect()` returns the adapter for the running version, and the
builders use it by default.

| Method                                              | Purpose                                                                            |
| --------------------------------------------------- | ---------------------------------------------------------------------------------- |
| `Optional<RenderBackendType> renderBackend()`       | Which backend Minecraft is running; empty before its GPU device exists.             |
| `String minecraftVersion()`                         | For diagnostics.                                                                   |
| `boolean isOnRenderThread()`                        | Guards every GPU call.                                                             |
| `<T> Optional<T> backendAccess(Class<T>)`           | Hands Mica's internal renderers their backend-specific host access (`VulkanHostAccess`, `OpenGLHostAccess`). Not for mod code. |
| `Optional<SpriteBounds> locateSprite(Identifier, Identifier)` | UV bounds for an arbitrary sprite in a host atlas.                       |
| `Optional<SpriteBounds> locateItemIcon(ItemStack)` | UV bounds for an item sprite in the items atlas.                                  |

The interface is backend-independent. The Vulkan-typed methods it used to have
(`currentVulkanContext()`, `activeCommandBuffer()`, `vkImageViewFor()`,
`isVulkanRendererActive()`) now live on `MinecraftCompatImpl_26_2` (and the internal
`VulkanHostAccess`) only.

--------------------------------------------------- | ---------------------------------------------------------------------------------- |
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

public enum TextureFilter { LINEAR, NEAREST }

public record TextureHandle(@NotNull Identifier atlasId, long imGuiTextureId) implements MicaTexture;
```

A `SpriteBounds` is the UV rect of a sprite inside a Minecraft atlas. A `TextureHandle`
is a per-frame snapshot returned by `registerAtlasTexture`; its `close()` is a no-op
(the renderer owns the registration). Prefer `MicaTexture` from `texture(...)` for
anything you keep across frames.

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
