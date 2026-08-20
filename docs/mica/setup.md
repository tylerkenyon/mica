# Setup: install Mica into your mod

This page covers the consumer side: pulling Mica into another mod project, dropping it
into `/libs/`, declaring the dependency in `fabric.mod.json`, and the runtime
initialisation step on your client entrypoint.

## 1. Acquire the jar

Download `mica-<version>.zip` from the GitHub Releases page. There is no Maven Central
publication yet; every release ships as a GitHub asset.

Unzip:

```
mica-<version>-lib/
├── mica-<version>.jar
├── LICENSE.txt
└── library-README.md
```

Copy `mica-<version>.jar` into your consumer mod's `libs/` directory.

## 2. Wire the dependency in your build script

`build.gradle` (Groovy DSL):

```groovy
dependencies {
    implementation files("libs/mica-<version>.jar")
}
```

`build.gradle.kts` (Kotlin DSL):

```kotlin
dependencies {
    implementation(files("libs/mica-<version>.jar"))
}
```

You can also wire `libs/` as a flat-dir repository and request the file by a fixed
name. The file-deps path is the simplest.

```
└── your-mod/
    ├── build.gradle
    ├── libs/
    │   └── mica-0.1.jar       (drop the version you ship)
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
    "minecraft": "~26.2"
  },
  "custom": {
    "mica:required": true,
    "mica:version": "0.1"
  }
}
```

## 4. Initialise the renderer

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

The mixin in Mica (loaded with the consumer mod because Mica's jar carries the mixin
config) drives the per-frame `prepareForFrame()` and `renderOverlay()` calls. The
mixin only runs on the render thread, on the client side, after `GuiRenderer`
finishes its vanilla GUI submissions.

## 5. Tear down on shutdown

```java
ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
    ActiveRenderers.set(null);
    renderer.close();
});
```

`close()` releases Vulkan descriptor pools, the font atlas upload resource, and the
two ping-pong blur targets.

## What goes wrong if you skip a step

| You skip…                                     | You see…                                                          |
| --------------------------------------------- | ----------------------------------------------------------------- |
| `withMinecraftCompat(...)`                    | `IllegalStateException` at builder time.                           |
| The `libs/` drop                              | `NoClassDefFoundError: dev/technix/mica/api/OverlayRenderer` on the first class load. |
| `ActiveRenderers.set(renderer)`               | Renderer exists but none of its `renderOverlay()` triggers fire.   |
| `withFrostedGlass(true)` (omit)               | Panes draw without a backdrop. Use the explicit `Draw.frostedPanel` overloads to hand-draw the backdrop. |

## OpenGL fallback

If Minecraft 26.2 picks the OpenGL backend, Mica's renderer is silently inert: the
mixin calls `prepareForFrame()` and `renderOverlay()`, both of which `isVulkanActive()`
gates. A single WARN line appears in the log:

```
imgui-mc-impl requires Vulkan — host GpuDevice is active as X, not VulkanDevice.
```

Run with `--graphicsBackend vulkan` on the launcher command line to force Vulkan.

## Where to go next

* [`elements.md`](./elements.md) — the `OverlayElement` interface.
* [`contexts.md`](./contexts.md) — the `MicaScreen` enum.
* [`customisation.md`](./customisation.md) — fonts and glass.
* [`api.md`](./api.md) — symbol reference.
