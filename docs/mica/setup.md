# Setup: install Mica into your mod

This page covers the consumer side: pulling Mica into another mod project, dropping it
into `/libs/`, declaring the dependency in `fabric.mod.json`, and the runtime
initialisation step on your client entrypoint.

## 1. Acquire the jar

Download the zip for your Minecraft version from the GitHub Releases page:
`mica-lib-<version>+mc26.2.zip` or `mica-lib-<version>+mc26.3.zip`. There is no Maven
Central publication yet; every release ships as GitHub assets. The API is the same in both
([`multiversion.md`](./multiversion.md)).

Unzip:

```
mica-lib-<version>+mc<minecraft>/
├── mica-lib-<version>+mc<minecraft>.jar
├── LICENSE.txt
└── library-README.md
```

Copy the jar into your consumer mod's `libs/` directory.

## 2. Wire the dependency in your build script

`build.gradle` (Groovy DSL):

```groovy
dependencies {
    implementation files("libs/mica-lib-<version>+mc26.3.jar")
}
```

`build.gradle.kts` (Kotlin DSL):

```kotlin
dependencies {
    implementation(files("libs/mica-lib-<version>+mc26.3.jar"))
}
```

You can also wire `libs/` as a flat-dir repository and request the file by a fixed
name. The file-deps path is the simplest.

```
└── your-mod/
    ├── build.gradle
    ├── libs/
    │   └── mica-lib-0.1+mc26.3.jar   (the Mica version + your Minecraft version)
    └── src/main/...
```

## 3. Declare the dependency in `fabric.mod.json`

Fabric Loader does not parse Mica; the entry below is an end-user-visible declaration
that your mod requires Mica to be present. Pick any string convention; Mica does not
read this field.

```json
{
  "id": "yourmod",
  "version": "${version}",
  "depends": {
    "fabricloader": ">=0.19.0",
    "minecraft": "~26.3"
  },
  "custom": {
    "mica:required": true,
    "mica:version": "0.1"
  }
}
```

## 4. Initialise Mica

```java
public final class FabricClientEntry implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Mica mica = Mica.create();
        mica.registerOverlay(new MyHudElement());
    }
}
```

`Mica.create()` builds the renderer, installs it as the active overlay and registers a
Fabric `CLIENT_STOPPING` hook that closes it. Nothing touches the GPU yet: Minecraft has
not created its device at `onInitializeClient` time. On the first frame, Mica detects
whether Minecraft runs **Vulkan or OpenGL** and starts the matching renderer. Your code
does not change between the two ([`backends.md`](./backends.md)).

The mixin in Mica (loaded with the consumer mod because Mica's jar carries the mixin
config) drives the per-frame `prepareForFrame()` and `renderOverlay()` calls. The
mixin only runs on the render thread, on the client side, after `GuiRenderer`
finishes its vanilla GUI submissions.

Options live on the builder:

```java
Mica mica = Mica.builder()
        .frostedGlass(true)
        .frostedGlassStyle(myStyle)
        .fontRegistry(myFonts)
        .closeOnClientStop(true)      // default
        .build();
```

## 5. Tear down on shutdown

Automatic with `Mica.create()`. If you disabled `closeOnClientStop`, call
`mica.close()` on the render thread (for example from `CLIENT_STOPPING`). It
releases every GPU resource Mica owns (Vulkan descriptor pools and blur targets, or
OpenGL programs, buffers, textures and framebuffers) and the ImGui context.

### Migrating from `OverlayRenderer.builder()`

The 0.1 setup still compiles and works on both backends:

```java
OverlayRenderer renderer = OverlayRenderer.builder()
        .withMinecraftCompat(new MinecraftCompatImpl_26_2())   // now optional
        .withFrostedGlass(true)
        .build();
renderer.registerElement(new MyHudElement());
ActiveRenderers.set(renderer);                                  // internal; prefer Mica
```

`withMinecraftCompat` is optional now (`MinecraftCompat.detect()` returns the adapter
for the jar's Minecraft version; `MinecraftCompatImpl_26_2` only exists in the 26.2 jar,
`MinecraftCompatImpl_26_3` only in the 26.3 jar).
`MinecraftCompat` itself lost its Vulkan-typed methods (`currentVulkanContext()`,
`activeCommandBuffer()`, `vkImageViewFor()`, `isVulkanRendererActive()`). They
remain public on `MinecraftCompatImpl_26_2`, but are no longer part of the
backend-independent interface.

## What goes wrong if you skip a step

| You skip…                                     | You see…                                                          |
| --------------------------------------------- | ----------------------------------------------------------------- |
| The `libs/` drop                              | `NoClassDefFoundError: dev/technix/mica/api/Mica` on the first class load. |
| `Mica.create()` (building an `OverlayRenderer` by hand and never installing it) | Renderer exists but none of its `renderOverlay()` triggers fire.   |
| `frostedGlass(true)` (set to `false`)         | Panes draw without a backdrop.                                    |

## Rendering backends

Both rendering backends are supported on 26.2 and 26.3, and Mica follows whichever one
Minecraft chose. If Mica cannot start a renderer for it, the log gets a single ERROR
like:

```
Mica could not start its renderer (Minecraft 26.2, rendering backend OpenGL, Mica 0.1).
Reason: ... The overlay is disabled for this session.
```

The game keeps running; only the overlay is off. See
[`troubleshooting.md`](./troubleshooting.md).

## Where to go next

* [`elements.md`](./elements.md) — the `OverlayElement` interface.
* [`contexts.md`](./contexts.md) — the `MicaScreen` enum.
* [`customisation.md`](./customisation.md) — fonts and glass.
* [`api.md`](./api.md) — symbol reference.
