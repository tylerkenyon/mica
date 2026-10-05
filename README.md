# Mica

A Dear ImGui overlay library for Minecraft 26.2, on both Vulkan and OpenGL.

Build custom HUDs, panels and interfaces directly inside Minecraft's render
pipeline. Mica detects whether Minecraft runs Vulkan or OpenGL and picks the
matching renderer; your mod's code is the same for both.

---

## Sponsors

<!--
This section is intentionally manual. When you take on a sponsor, replace this block
with the sponsor's name, link, and a one-line blurb. Format suggestion:
[Sponsor Name](http
  * **s://sponsor-link)** — short note about what they funded.
  * **[Another Sponsor](https://another-link)** — short note.

Until you have sponsors, leave this comment in place and the section renders empty.
-->

**[RiseLimits](https://limits.riseclient.com)** — Providing AI Credits + Discounted Official Models!
---

## What Mica is

A vector-overlay library for Minecraft 26.2 (Vulkan and OpenGL), distributed as a
single jar. You author an `OverlayElement` (or a `ctx -> { ... }` lambda) that emits
Dear ImGui draw calls into a Minecraft `RenderContext`, register it with `Mica`, and your element is
drawn every frame behind (or on top of) the vanilla UI on whichever screens you
declared. The platform handles backend detection, the frosted-glass backdrop, GPU
texture management, the atlas sprite lookup, the screen-context filter, the font atlas
loading, and the mixin that hooks into `GuiRenderer.render()`. You write the HUD.

Three things Mica is not:

* **Not a mod.** The published jar carries no `fabric.mod.json`. It registers as
  library code when dropped into a consumer mod's `/libs/`.
* **Not an input-redirection hack.** It draws with the host's own GPU device (its
  Vulkan frame command buffer, or its OpenGL context). Mouse / keyboard capture is
  opt-in and lives in the bundled input mixins; nothing reaches vanilla game-state.
* **Not backend-specific.** The public API never mentions Vulkan or OpenGL. Minecraft
  26.2's Vulkan and OpenGL backends are both supported; the backend is chosen at runtime
  from what Minecraft is actually using (see [`docs/mica/backends.md`](docs/mica/backends.md)).

## Highlights

| Feature                       | What it does                                                                                          |
| ----------------------------- | ----------------------------------------------------------------------------------------------------- |
| Vulkan **and** OpenGL         | One backend-independent API; the renderer matching Minecraft's backend is picked automatically.      |
| Single-jar dispatch           | Mica jars imgui-java; LWJGL Vulkan/OpenGL come from Minecraft 26.2's own libraries.                   |
| Opaque textures               | `MicaTexture` wraps Minecraft textures/atlases for ImGui on every backend, with explicit, thread-safe lifetime. |
| Drop into `/libs/`            | Consumers do not need `fabric.mod.json` semantics from Mica; the published jar registers as plain library code, not a conflicting mod entry. |
| Render scopes                 | Each element declares a `MicaScreen` (`TITLE`, `IN_GAME_HUD`, `PAUSE`, `INVENTORY`, `CHAT`, `ANY`). The platform consults `Minecraft.getInstance().gui.screen()` each frame. |
| Per-element isolation         | A throwing `render()` disables that element only; the rest of the overlay continues to draw.          |
| Custom fonts                  | `FontRegistry.add(name, fileName, sizePx)` reads from any directory in your mod jar.                  |
| Custom glass                  | `FrostedGlassStyle.builder()` re-skins blur scale, passes, tint, border, and rounding per call.       |
| Version adapters              | `MinecraftCompat`-shaped bridge isolates the public API from the 26.2-specific rendering internals. Future Minecraft versions become new `MinecraftCompatImpl_vXX_X` classes. |

## Where to read more

| Path                                                    | What's in it                                                                   |
| ------------------------------------------------------- | ------------------------------------------------------------------------------ |
| [`docs/mica/setup.md`](docs/mica/setup.md)             | Pulling the jar in, dropping it into `/libs/`, declaring it in `fabric.mod.json`. |
| [`docs/mica/elements.md`](docs/mica/elements.md)       | The `OverlayElement` lifecycle, `renderScope`, `isVisible`, a worked example. |
| [`docs/mica/contexts.md`](docs/mica/contexts.md)       | The `MicaScreen` enum and how the renderer filters it.                         |
| [`docs/mica/customisation.md`](docs/mica/customisation.md) | Wiring your own `FontRegistry` and `FrostedGlassStyle`.                         |
| [`docs/mica/api.md`](docs/mica/api.md)                  | Symbol-by-symbol reference.                                                   |
| [`docs/mica/backends.md`](docs/mica/backends.md)        | Vulkan + OpenGL support, automatic detection, textures, backend differences, adding a backend. |
| [`docs/mica/vulkan.md`](docs/mica/vulkan.md)            | Vulkan specifics of the 26.2 backend.                                          |
| [`docs/mica/imgui.md`](docs/mica/imgui.md)              | imgui-java notes that are not obvious from the upstream README.                |
| [`docs/mica/internals.md`](docs/mica/internals.md)      | What each class in `internal/` does, and why.                                  |
| [`docs/mica/troubleshooting.md`](docs/mica/troubleshooting.md) | Common failures: renderer start-up errors, missing font, atlas texture mismatch. |
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
        // Picks the Vulkan or OpenGL renderer automatically, installs the overlay and
        // closes it when the client stops.
        Mica mica = Mica.create();

        mica.registerOverlay(ctx -> {
            ImGui.begin("My Mod");
            ImGui.text("Hello Minecraft!");
            if (ImGui.button("Click me")) {
                // ...
            }
            ImGui.end();
        });

        mica.registerOverlay(new MyHudElement());   // full OverlayElement: screen scope, visibility
    }
}
```

The bundled `examples.ToastElement` is in the jar; copy its structure when writing
your own.

## Requirements

* Minecraft **26.2**, on the **Vulkan** or the **OpenGL** backend (detected at runtime).
* Fabric Loader and Fabric API on the consumer end, with Mica in `/libs/`.
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
* `dev.technix.mica.api.compat.v26_2.*` — the version adapter: rendering-backend
  detection plus Vulkan and OpenGL host access. New Minecraft releases become new
  adapters; the public API does not need to change.
* `dev.technix.mica.internal.*` — the backend-independent core (`ImGuiRenderer`:
  ImGui context, frame lifecycle, textures), the screen detector, the input router,
  the font atlas, the active-renderer registry.
* `dev.technix.mica.internal.backend.{vulkan,opengl}.*` — the two `RenderBackend`
  implementations. The only code that touches Vulkan or OpenGL.

Public by Java visibility, `internal.*` is conceptually private; application code
should not depend on it. `ApiBoundaryTest` (part of `./gradlew test`) fails the
build if the public API or the core references Vulkan/OpenGL types.

## Safety and licensing

* The bundled SF Pro Display fonts ship in the public jar. They are Apple's
  typeface, and the substitution path via `ImGuiFonts` is one line if you want to
  swap in Inter for a commercial build.
* Vulkan and OpenGL. If Mica cannot start a renderer for the backend Minecraft is
  using, it logs one ERROR (Minecraft version, backend, Mica version, reason) and
  disables only the overlay; it never switches Minecraft's backend.
* Mica does not input-redirect. It only draws.

## References

* Dear ImGui upstream — https://github.com/ocornut/imgui
* imgui-java binding — https://github.com/SpaiR/imgui-java
* Vulkan 1.x spec — https://registry.khronos.org/vulkan/
* OpenGL 3.3 core spec — https://registry.khronos.org/OpenGL/
* Minecraft 26.2 (`com.mojang.blaze3d.vulkan`, `com.mojang.blaze3d.opengl`) —
  `GuiRenderer` and `RenderTarget` are the entrypoints behind `MinecraftCompatImpl_26_2`.

## License

`LICENSE.txt` (next to this README) is the canonical license for the published jar.
The slim library jar inherits it; flip the field in `LICENSE.txt` to a permissive
licence of your choosing before tagging a `v*` release of your own.

— Mica
