---
name: mica
description: Reference for working inside the Mica library project. Use when the user asks about Mica's API, asks to add or change a Mica HUD element, asks about the Vulkan backend in Minecraft 26.2+, asks to ship the slim jar as a release, or asks to plug Mica into another Fabric mod.
---

# Mica

Mica is a thin Dear ImGui-on-Minecraft-26.2+ Vulkan overlay library. The published
artefact is a slim jar a consumer modder drops into `/libs/`; `fabric.mod.json` is
stripped, so Fabric Loader treats it as plain library code, not a mod.

When asked to do anything that touches the Minecraft / ImGui / Vulkan plumbing, **read
this skill doc and the package's own javadoc first**, then check the `docs/mica/`
folder for the corresponding design doc before writing code.

## Public surface ↔ package map

| Package                                        | What lives there                                                  |
| ---------------------------------------------- | ----------------------------------------------------------------- |
| `dev.technix.mica.api`                         | Public API: `OverlayRenderer`, `OverlayElement`, `RenderContext`, `MicaScreen`, `Palette`, `FontRegistry`, `FontFace`, `FrostedGlassStyle`, `Draw`primitives, `SpriteBounds`, `TextureFilter`, `TextureHandle`, `VanillaAtlases`. |
| `dev.technix.mica.api.compat.v26_2`            | `MinecraftCompatImpl_26_2` — the version adapter that reaches the host's Vulkan device, command buffer and atlas sprites. The only file in the project that imports `com.mojang.*`. |
| `dev.technix.mica.internal`                    | Renderer (`ImGuiRenderer`), Vulkan backend (`VulkanImGuiBackend`, `FrostedGlassRenderer`, `VulkanShaderCompiler`), input routing (`ImGuiInputRouter`), screen detection (`ScreenDetector`), font atlas (`ImGuiFonts`, `FontLoader`), `ActiveRenderers`. |
| `dev.technix.mica.mixin.client`                | Mixin accessors required by the v26_2 adapter — bridge into Mojang's Vulkan bindings. |
| `dev.technix.mica.examples`                    | `ToastElement` — a reference `OverlayElement`. The shipped icon for the library's "how do I render" surface. |

## Symbol entry points (use this table when the user asks "where is X")

| Symbol                                              | Package                                              |
| --------------------------------------------------- | ---------------------------------------------------- |
| `OverlayRenderer.builder()` / `setGlassStyle(...)`  | `dev.technix.mica.api.OverlayRenderer`               |
| `FontRegistry` / `FontFace`                         | `dev.technix.mica.api.FontRegistry`                  |
| `FrostedGlassStyle.builder()`                       | `dev.technix.mica.api.FrostedGlassStyle`             |
| `OverlayElement.renderScope()` / `isVisible()` / `render()` | `dev.technix.mica.api.OverlayElement`        |
| `MicaScreen` enum                                   | `dev.technix.mica.api.MicaScreen`                    |
| `RenderContext` record                              | `dev.technix.mica.api.RenderContext`                 |
| `MinecraftCompat` interface                         | `dev.technix.mica.api.MinecraftCompat`               |
| `MinecraftCompatImpl_26_2`                          | `dev.technix.mica.api.compat.v26_2.*`                |
| `Draw.frostedPanel` / `Draw.text` / `Draw.image`    | `dev.technix.mica.internal.util.Draw` (called from `api`) |
| `ActiveRenderers.set(renderer)`                     | `dev.technix.mica.internal.ActiveRenderers` (called from `api`) |

## Authoring rules

* **Mica is Vulkan-only.** If the host picks OpenGL, the compat layer logs a one-shot
  WARN and elements draw nothing. `build.gradle` pins `--graphicsBackend vulkan` for
  the `runClient` task so dev always sees the overlay.
* **Mica is not a hack-client.** It does not redirect input; it only draws.
* **`screen` lives on `gui.screen()` in 26.2.** The old `Minecraft.screen` field moved
  to `Minecraft.gui.screen()`; `ScreenDetector` uses the new accessor. **Do not regress
  to `minecraft.screen`** — it will not compile.
* **Resources must live in `Micro$oft`'s scope.** Resource locations for fonts are
  `Identifier.fromNamespaceAndPath(<mod-id>, "<dir>")`. The default Mica fonts ship at
  `assets/mica/font/*.otf`; user fonts live in `assets/<mod-id>/<dir>/*.otf`.
* **`FontRegistry.add(name, fileName, sizePx)` must be called after the ImGui context
  is alive.** Calling it from `onInitializeClient` crashes because the resource manager
  is null and ImGui has no context yet. Hook into
  `ClientLifecycleEvents.CLIENT_STARTED.register(client -> { ... })` for runtime
  font registration, then call `FontRegistry.commitPendingFaces()`.
* **Do not** import `com.mojang.blaze3d.vulkan.*` from anywhere in `api.*` — version-
  specific code belongs in `api.compat.v26_2.*`. The compat layer is the only place
  that is allowed to reach into Mojang.
* **Do not** add a class to a public package without making sure it is in `api/` or
  `examples/`. Anything in `internal/` is by convention private; code that reaches
  into it from outside Mica will break on the next refactor.

## Dist pipeline — slim jar

The published library is the slim jar produced by the `libraryJar` task in
`build.gradle`. The flow:

```
./gradlew build            → build/libs/<name>-<version>.jar   (the full project)
                              │
                              ▼ (recopy everything except `fabric.mod.json`)
./gradlew libraryJar       → build/dist-staging/<name>-lib-<version>.jar
                              │
                              ▼ (zip with LICENSE + library-README)
./gradlew dist             → dist/<name>-lib-<version>.zip
```

`./gradlew dist` is the production-ready command. On Windows, `dist.bat` is a shim.

### Verifying the slim jar is correct

```
unzip -l dist/<name>-lib-<version>.jar | grep "tech/"
```

Expected: `dev/technix/mica/**` is present (the entire library); no other `dev/technix/**`
classes; `fabric.mod.json` is absent (so /libs/ loaders don't pick it up as a
conflicting mod entry instead of recognizing it as library code); `assets/mica/font/**`
is present.

### Cutting a release

The GitHub Actions workflow `.github/workflows/build-and-release.yml` runs the full
pipeline on every push to `main` and on every PR against `main`. To ship a numbered
release:

```
# 1. Bump mod_version in gradle.properties if the release is a new version.
#    Leave as-is if you only want a new dev pre-release on the next push.

# 2. Tag the commit — the workflow flips prerelease: false on v* tags:
git tag v0.2.0
git push origin v0.2.0

# 3. The workflow builds + publishes mica-<version>-lib-<version>.zip to GitHub Releases.
```

Every push to `main` automatically publishes a `dev-<short-sha>` PRE-release. PRs
from forks do not get releases (only build verify).

### Creating a public GitHub repo via the gh CLI

```
# Authenticate once:
gh auth login

# From the project root:
gh repo create mica --public --source=. --remote=upstream --push \
        --description "A small Dear ImGui port for Minecraft 26.2+ (Vulkan-only)."
```

`--push` initialises main with the local tree. Subsequent commits use
`git push upstream main`.

### Verification checklist for "the slim jar is ready for release"

1. `unzip -l dist/<name>-lib-<version>.jar` shows only `dev/technix/mica/**`,
   `assets/mica/**`, the mixin accessors in `dev/technix/mica/mixin/client/**`,
   `mica.mixins.json`, `mica.client.mixins.json`, and `META-INF/jars/**`. No
   `fabric.mod.json`. No personal namespace.
2. `mvn -q dependency:tree -f build.gradle 2>/dev/null` shows only the public deps.
   (Standard fabric-loader / Mojang deps, not the author's editor tooling.)
3. `./gradlew runClient` boots into the world with the overlay visible.

If any of those fail, fix before tagging `v*`.

## Useful Gradle flags for live debugging

* `-PimguiQuickPlay="New World"`: boots straight into a singleplayer world so the
  in-game HUD is the first thing on screen.
* `-PimguiDebugClear` / `-PimguiDebugSkipBlur` / `-PimguiDebugSkipDraw`: halves of the
  render pipeline that can be skipped for bisecting a misbehaving layer.
* `-PimguiAllowNonVulkan`: opt out of the `--graphicsBackend vulkan` pin when running
  on a non-Vulkan machine.

## Reading order for someone studying the project

1. `README.md`
2. `docs/mica/README.md`
3. `docs/mica/setup.md`, `docs/mica/elements.md`, `docs/mica/customisation.md`
4. `docs/mica/api.md`, `docs/mica/vulkan.md`, `docs/mica/imgui.md`
5. `docs/mica/internals.md`, `docs/mica/troubleshooting.md`, `docs/mica/distribution.md`
