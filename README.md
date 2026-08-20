# Mica

A small Dear ImGui port for Minecraft that draws panels and HUDs into the same Vulkan
framebuffer the game itself renders into. Vulkan-only on Minecraft 1.26+, distributed
as a slim jar that does not register itself as a mod when dropped into another
modder's `/libs/`.

> Mica is a thin transparent sheet for a reason. The whole point is to get out of the
> way.

---

## Sponsors

<!--
This section is intentionally manual. When you take on a sponsor, replace this block
with the sponsor's name, link, and a one-line blurb. Format suggestion:

  * **[Sponsor Name](https://sponsor-link)** — short note about what they funded.
  * **[Another Sponsor](https://another-link)** — short note.

Until you have sponsors, leave this comment in place and the section renders empty.
-->

_None yet._

---

## What Mica is

A vector-overlay library for Minecraft 1.26, Vulkan-only, distributed as a single
jar. You author an `OverlayElement` that emits Dear ImGui draw calls into a
Minecraft `RenderContext`, register it with an `OverlayRenderer`, and your element is
drawn every frame behind (or on top of) the vanilla UI on whichever screens you
declared. The platform handles the frosted-glass backdrop, the descriptor-set
management, the atlas sprite lookup, the screen-context filter, the font atlas
loading, and the mixin that hooks into `GuiRenderer.render()`. You write the HUD.

Three things Mica is not:

* **Not a mod.** The published jar carries no `fabric.mod.json`. It registers as
  library code when dropped into a consumer mod's `/libs/`.
* **Not an input-redirection hack.** It forwards vertex and pixel work to the host's
  existing `VkCommandBuffer`. Mouse / keyboard capture is opt-in and lives in the
  bundled input mixins; nothing reaches vanilla game-state.
* **Not cross-backend.** Mica is Vulkan-only on Minecraft 1.26+. The Mojang
  `com.mojang.blaze3d.vulkan` package is the integration surface; OpenGL is a
  runtime failure (logged once at WARN level — the overlay becomes invisible).

## Highlights

| Feature                       | What it does                                                                                          |
| ----------------------------- | ----------------------------------------------------------------------------------------------------- |
| Single-jar dispatch           | Mica jars imgui-java and the LWJGL Vulkan bits stand-alone via Minecraft 1.26's own `lwjgl-vulkan`.    |
| Drop into `/libs/`            | Consumers do not need `fabric.mod.json` semantics from Mica; the published jar registers as plain library code, not a conflicting mod entry. |
| Render scopes                 | Each element declares a `MicaScreen` (`TITLE`, `IN_GAME_HUD`, `PAUSE`, `INVENTORY`, `CHAT`, `ANY`). The platform consults `Minecraft.getInstance().gui.screen()` each frame. |
| Per-element isolation         | A throwing `render()` disables that element only; the rest of the overlay continues to draw.          |
| Custom fonts                  | `FontRegistry.add(name, fileName, sizePx)` reads from any directory in your mod jar.                  |
| Custom glass                  | `FrostedGlassStyle.builder()` re-skins blur scale, passes, tint, border, and rounding per call.       |
| Version adapters              | `MinecraftCompat`-shaped bridge isolates the public API from the 26.2-specific Vulkan context. Future Minecraft versions become new `MinecraftCompatImpl_vXX_X` classes. |

## Where to read more

| Path                                                    | What's in it                                                                   |
| ------------------------------------------------------- | ------------------------------------------------------------------------------ |
| [`docs/mica/setup.md`](docs/mica/setup.md)             | Pulling the jar in, dropping it into `/libs/`, declaring it in `fabric.mod.json`. |
| [`docs/mica/elements.md`](docs/mica/elements.md)       | The `OverlayElement` lifecycle, `renderScope`, `isVisible`, a worked example. |
| [`docs/mica/contexts.md`](docs/mica/contexts.md)       | The `MicaScreen` enum and how the renderer filters it.                         |
| [`docs/mica/customisation.md`](docs/mica/customisation.md) | Wiring your own `FontRegistry` and `FrostedGlassStyle`.                         |
| [`docs/mica/api.md`](docs/mica/api.md)                  | Symbol-by-symbol reference.                                                   |
| [`docs/mica/vulkan.md`](docs/mica/vulkan.md)            | Vulkan specifics of the 26.2 backend.                                          |
| [`docs/mica/imgui.md`](docs/mica/imgui.md)              | imgui-java notes that are not obvious from the upstream README.                |
| [`docs/mica/internals.md`](docs/mica/internals.md)      | What each class in `internal/` does, and why.                                  |
| [`docs/mica/troubleshooting.md`](docs/mica/troubleshooting.md) | Common failures: OpenGL fallback, missing font, atlas texture mismatch. |
| [`docs/mica/distribution.md`](docs/mica/distribution.md) | The slim jar and how to publish.                                              |

## Quick start for consumers

The published artifact is in your consumer mod's `/libs/` folder as
`mica-<version>.jar`. In your `build.gradle`:

```groovy
repositories { mavenCentral() }

dependencies {
    implementation files("libs/mica-<version>.jar")
}
```

Then, on the client side:

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

The bundled `examples.ToastElement` is in the jar; copy its structure when writing
your own.

## Requirements

* Minecraft **26.2** with the **Vulkan** backend. Mica is Vulkan-only.
* Fabric Loader on the consumer end, with Mica in `/libs/`.
* A JDK that matches loom's `targetJavaVersion` (currently 25).

## Build and publishing

The library is delivered via a slim jar that strips authoring-side HUDs before
publishing.

| Task                         | Output                                                                                |
| ---------------------------- | ------------------------------------------------------------------------------------- |
| `./gradlew build`            | The whole project (includes any out-of-tree HUD examples you keep). Not for distribution. |
| `./gradlew libraryJar`       | The slim library jar only (consumer-facing artefact).                                  |
| `./gradlew dist`             | `mica-<version>.zip` containing the slim jar plus `LICENSE.txt` and `library-README.md`. |
| `./gradlew clean`            | Wipes `build/`. Useful to recover from a stale-build trap.                            |

The release pipeline (build verify + dev pre-release on every push, full release on
`v*` tag) is in [`.github/workflows/build-and-release.yml`](.github/workflows/build-and-release.yml).
See [`docs/mica/distribution.md`](docs/mica/distribution.md) for what the slim jar
contains and excludes.

## Architectural notes

* `dev.technix.mica.api.*` — the public surface. Consumers import from here.
* `dev.technix.mica.api.compat.v26_2.*` — the version adapter. New Minecraft releases
  become new adapters; the public API does not need to change.
* `dev.technix.mica.internal.*` — the renderer, the Vulkan backend, the screen
  detector, the input router, the font atlas, the active-renderer registry. Public
  by Java visibility, conceptually private. Application code should not depend on
  these symbols.

The boundary is enforced socially: PRs that cross-package-import from `internal.*`
into `api.*` get rejected. There is no ArchUnit-as-build-step here; the doc warns
authors.

## Safety and licensing

* The bundled SF Pro Display fonts ship in the public jar. They are Apple's
  typeface, and the substitution path via `ImGuiFonts` is one line if you want to
  swap in Inter for a commercial build.
* Vulkan-only. Mica will not render under OpenGL. A WARN log fires once when the
  host picks OpenGL, and the overlay becomes invisible.
* Mica does not input-redirect. It only draws.

## References

* Dear ImGui upstream — https://github.com/ocornut/imgui
* imgui-java binding — https://github.com/SpaiR/imgui-java
* Vulkan 1.x spec — https://registry.khronos.org/vulkan/
* Minecraft 1.26+ (`com.mojang.blaze3d.vulkan`) — `GuiRenderer` and `RenderTarget`
  are the entrypoints behind `MinecraftCompatImpl_26_2`.

## License

`LICENSE.txt` (next to this README) is the canonical license for the published jar.
The slim library jar inherits it; flip the field in `LICENSE.txt` to a permissive
licence of your choosing before tagging a `v*` release of your own.

— Mica
