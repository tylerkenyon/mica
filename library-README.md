# mica — ImGui-style overlay library for Minecraft 26.2+ (Vulkan)

A small Dear ImGui port that draws HUDs, toast notifications, and procedural text/sprite
overlays into the same Vulkan framebuffer the game itself renders into, on Minecraft
26.2+ with the official `com.mojang.blaze3d.vulkan` backend.

## What's in this artefact

| Package                            | What it is                                                                |
| ---------------------------------- | ------------------------------------------------------------------------- |
| `dev.technix.mica.api`             | The public API — `OverlayRenderer`, `OverlayElement`, `RenderContext`, `Draw`, `Palette`, `FontRegistry`, `FontFace`, `FrostedGlassStyle`, `MicaScreen`, `SpriteBounds`, `TextureFilter`, `TextureHandle`, `VanillaAtlases`. |
| `dev.technix.mica.api.compat.v26_2` | `MinecraftCompatImpl_26_2` — the version adapter that reaches the host's Vulkan device, command buffer and atlas sprites. |
| `dev.technix.mica.examples`        | `ToastElement` — a reference `OverlayElement`.                            |
| `dev.technix.mica.internal`        | The renderer, the Vulkan backend, the input router, the screen detector, the font atlas loader. Public by Java visibility, conceptually private — application code should not depend on these symbols. |
| `dev.technix.mica.mixin.client`    | Mixin accessors required by the v26_2 adapter.                              |
| `assets/mica/`                     | Bundled font assets (SF Pro Display — see "Licensing" below).             |

The `fabric.mod.json` is intentionally **not** present in this artefact. A host's Fabric
Loader treats the file as plain library code when dropped into `/libs/`, rather than
as a separate mod entry.

## Requirements

* Minecraft **26.2** with the **Vulkan** backend. Mica is Vulkan-only.
* Fabric Loader on the consumer side, with this jar in the consumer mod's `/libs/`.
* A JDK that matches loom's `targetJavaVersion` (currently 25).

If your stack picks the OpenGL backend, the overlay is invisible. See
[the OpenGL fallback note](#opengl-fallback).

## Install into your mod

1. Copy this jar — `mica-<version>.jar` — into your mod project's `libs/` folder.
2. Add an explicit file dependency in your `build.gradle`:

   ```groovy
   dependencies {
       implementation files("libs/mica-<version>.jar")
   }
   ```
3. Declare a `dependencies` entry in your `fabric.mod.json` so other modders know
   yours needs this jar:

   ```json
   {
     "id": "yourmod",
     "depends": {
       "fabric-loader": ">=0.19.0",
       "minecraft": "~26.2"
     },
     "custom": {
       "mica:required": true,
       "mica:version": "<version>"
     }
   }
   ```

   The custom key is a convention. Fabric Loader does not interpret it; end-users
   follow the README.

## First-overlay quick start

```java
public final class FabricClientEntry implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        OverlayRenderer renderer = OverlayRenderer.builder()
                .withMinecraftCompat(new MinecraftCompatImpl_26_2())
                .withFrostedGlass(true)
                .build();

        renderer.registerElement(new MyHudElement());

        ActiveRenderers.set(renderer);
    }
}
```

Where `MyHudElement` is anything you write that implements
[`OverlayElement`](docs/mica/elements.md). The bundled
[`ToastElement`](docs/mica/elements.md#worked-example-toast) is a runnable example
you can copy.

## Authoring HUDs

See:

* [`docs/mica/elements.md`](docs/mica/elements.md) — the `OverlayElement` interface
  and a full worked example.
* [`docs/mica/contexts.md`](docs/mica/contexts.md) — the `MicaScreen` enum and how the
  per-frame filter decides where your element draws.
* [`docs/mica/customisation.md`](docs/mica/customisation.md) — wiring your own
  `FontRegistry` and your own `FrostedGlassStyle`.
* [`docs/mica/api.md`](docs/mica/api.md) — symbol-by-symbol reference for every
  public type.

## OpenGL fallback

A Minecraft client that picks the OpenGL backend has no `VulkanContext`, so the
overlay's `MinecraftCompatImpl_26_2.vulkanDevice()` returns empty and `Draw.backdrop`
returns `false`. The platform logs a single WARN at startup:

> `imgui-mc-impl requires Vulkan — host GpuDevice is active as X, not VulkanDevice.`

If you need to support both, force Vulkan with the `--graphicsBackend vulkan`
command-line argument (the project's `build.gradle` does this for `runClient` by
default; in production, opt-in via your launcher / client settings).

## Java package

The package is `dev.technix.mica`. To rename it (e.g. to `<yourgroup>.<libname>.api`),
vendoring the source under a new group is the standard path:

```sh
grep -rl "dev\.technix\.mica" src/ | xargs sed -i 's/dev\.technix\.mica/<yourgroup>.<libname>/g'
```

The slim jar binaries ship under the existing group; the rename is for source-only
vendoring.

## Custom fonts

Ship your `.otf` / `.ttf` files inside your mod jar at `assets/<your-mod-id>/fonts/`,
build a `FontRegistry` pointing at that path, and chain `.withFontRegistry(...)` on
the builder:

```java
FontRegistry myFonts = new FontRegistry(
        Identifier.fromNamespaceAndPath("yourmod", "fonts"));
myFonts.add("heading", "heading-bold.otf", 22f);
myFonts.add("body",    "body-regular.otf", 14f);

OverlayRenderer renderer = OverlayRenderer.builder()
        .withMinecraftCompat(new MinecraftCompatImpl_26_2())
        .withFontRegistry(myFonts)
        .build();
```

Draw with `renderer.font("heading")` (returns `FontFace | null`) and pass it to `Draw.text`.

## Custom frosted glass

`FrostedGlassStyle` is a value-object record with five fields, built through a
validating builder:

```java
FrostedGlassStyle sleek = FrostedGlassStyle.builder()
        .blurScaleDivisor(3)
        .blurPasses(7)
        .defaultTint(ImColor.rgba(38, 42, 50, 235))
        .defaultBorder(ImColor.rgba(255, 255, 255, 32))
        .defaultRounding(14f)
        .build();
```

Pass it via `.withFrostedGlassStyle(sleek)` at construction, or swap it at runtime with
`renderer.setGlassStyle(sleek)` — pass count is hot-swapped, divisor triggers
a target reallocation on the next resize.

## References

* Dear ImGui upstream — https://github.com/ocornut/imgui
* imgui-java binding — https://github.com/SpaiR/imgui-java
* Vulkan 1.x spec — https://registry.khronos.org/vulkan/

## Licensing

`LICENSE.txt` (next to this README) describes the redistribution terms. Bundled font
assets (SF Pro Display by Apple Inc.) are present for out-of-the-box rendering quality;
substitute Inter (a one-line change in `ImGuiFonts` inside the platform's source) for
commercial use where Apple's licence is a concern.

— Mica
