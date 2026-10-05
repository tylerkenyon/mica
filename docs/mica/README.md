# Mica documentation

This folder holds the design notes and API reference for the Mica library. The
top-level README stays short and is the right place to start if you only want to
consume Mica; the pages here are the right place when you want to author against
it, debug it, extend it, or publish it.

## Pages

| Page                                       | Read this to…                                                                  |
| ------------------------------------------ | ------------------------------------------------------------------------------ |
| [`setup.md`](./setup.md)                   | drop the slim jar into `/libs/`, declare it in `fabric.mod.json`, call `Mica.create()`. |
| [`elements.md`](./elements.md)             | author a new `OverlayElement` for your HUD with a worked example.             |
| [`contexts.md`](./contexts.md)             | understand `MicaScreen` and how the renderer filters elements per frame.     |
| [`customisation.md`](./customisation.md)   | plug in your own fonts (a directory you control) or re-skin the frosted glass. |
| [`api.md`](./api.md)                       | symbol-by-symbol reference for every public type.                            |
| [`multiversion.md`](./multiversion.md)     | Minecraft 26.2 + 26.3 with Stonecutter: which jar, building, version-specific code, adding a version. |
| [`backends.md`](./backends.md)             | Vulkan + OpenGL support: automatic detection, textures, feature matrix, adding a backend. |
| [`vulkan.md`](./vulkan.md)                 | understand the 26.2-specific Vulkan pipeline (descriptor ring, layout transitions). |
| [`imgui.md`](./imgui.md)                   | use the imgui-java 1.92 binding cleanly inside a Mica overlay.               |
| [`internals.md`](./internals.md)           | extend the platform — what each `internal` class does, and why.              |
| [`troubleshooting.md`](./troubleshooting.md)| common failures (renderer start-up errors, missing font, atlas mismatch).   |
| [`distribution.md`](./distribution.md)     | publish Mica; what the slim jar contains and excludes; the GitHub release workflow. |

## How this is organised

The Mica runtime is intentionally small. Its job is to stay out of the way until
it is asked to do something, then to do that thing exactly once per frame, with
the GPU device the host is already using: Minecraft's Vulkan frame command buffer,
or its OpenGL context.

The layers from top to bottom:

1. **Public API** (`api.*`): what consumers import. Backend-independent; no Vulkan
   or OpenGL types.
2. **Core** (`internal.ImGuiRenderer`): ImGui context, frame lifecycle, fonts,
   textures, input. Talks only to the `RenderBackend` interface.
3. **Backends** (`internal.backend.vulkan`, `internal.backend.opengl`): the only
   code that touches a graphics API. Picked at runtime from what Minecraft uses.
4. **Compatibility adapters** (`api.compat.v26_2`, `api.compat.v26_3`): the version-specific bridge to
   the running Minecraft release (backend detection, Vulkan and OpenGL host access).
   Each is compiled only into its own Minecraft version's jar (Stonecutter, see
   [`multiversion.md`](./multiversion.md)); the public API does not change.

`internal.*` is public by Java visibility but conceptually private; application
code should not depend on these symbols.

The docs page set mirrors that hierarchy: the top-level pages (setup, elements,
contexts, customisation) are about authoring, the middle pages (api, backends,
vulkan, imgui) are about depth, the bottom pages (internals, distribution,
troubleshooting) are about extending or shipping.

## Where to start

* New to Mica? Start at [`setup.md`](./setup.md), then [`elements.md`](./elements.md).
* Debugging? Try [`troubleshooting.md`](./troubleshooting.md) first.
* Extending Mica? [`internals.md`](./internals.md), then [`api.md`](./api.md).
