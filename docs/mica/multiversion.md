# Multi-version builds (Stonecutter)

Mica supports **Minecraft 26.2** and **Minecraft 26.3** from one source tree, built with
[Stonecutter](https://stonecutter.kikugie.dev/). Every Minecraft version gets its own Mica
jar. The public API is identical in all of them, so mod code written against Mica compiles
unchanged for each version.

## For mod developers

Use the Mica jar that matches the Minecraft version your mod targets:

| Minecraft | Mica jar                        | Dist zip                         |
| --------- | ------------------------------- | -------------------------------- |
| 26.2      | `mica-lib-<version>+mc26.2.jar` | `mica-lib-<version>+mc26.2.zip`  |
| 26.3      | `mica-lib-<version>+mc26.3.jar` | `mica-lib-<version>+mc26.3.zip`  |

A multi-version mod that is itself built with Stonecutter can depend on the matching jar
per version, for example `implementation files("libs/mica-lib-0.1+mc${minecraft_version}.jar")`.

## Layout

```
settings.gradle          Stonecutter tree: versions('26.2', '26.3'), vcsVersion = '26.2'
stonecutter.gradle       root build script; holds the active version
build.gradle             per-version build, applied to every versions/<mc> subproject
gradle.properties        shared properties (mod version, imgui-java, test deps)
versions/26.2/gradle.properties   minecraft_version, loader_version, fabric_version
versions/26.3/gradle.properties   minecraft_version, loader_version, fabric_version
src/                     the one source tree, kept in the 26.2 state in git
```

Each `versions/<mc>` directory is a Gradle subproject (`:26.2`, `:26.3`). Its sources are
`src/`, run through Stonecutter's comment processor for that version
(`versions/<mc>/build/generated/stonecutter/...`), then compiled against that version's
Minecraft.

## Building

| Command                                  | What it does |
| ---------------------------------------- | ------------ |
| `./gradlew build`                        | Builds and tests **every** version. |
| `./gradlew :26.3:build`                  | One version only. |
| `./gradlew dist`                         | One library zip per version in `dist/`. |
| `./gradlew :26.3:runClient`              | Dev client on 26.3 (`-PmicaBackend=vulkan|opengl`, `-PmicaDemoWindow` work as before). |
| `./gradlew "Set active project to 26.3"` | Rewrites `src/` for 26.3, so the IDE shows and compiles the 26.3 code paths. |
| `./gradlew "Reset active project"`       | Back to the 26.2 state. **Run this before committing**. |

CI (`.github/workflows/build-and-release.yml`) runs `./gradlew build` and `./gradlew dist`,
so every push builds, tests and packages both versions.

## Writing version-specific code

Most of Mica is version-independent. Version-specific code is limited to:

* **The compat adapters.** `api/compat/v26_2/MinecraftCompatImpl_26_2.java` is wrapped in
  `//? if <26.3 {` … `//?}`, and `api/compat/v26_3/MinecraftCompatImpl_26_3.java` in
  `//? if >=26.3 {` … `//?}`. Each one only exists in its own version's jar.
  `MinecraftCompat.detect()` picks the right one with a version condition.
* **Mixin accessors** whose Mojang targets moved (`GpuDeviceAccessor`,
  `CommandEncoderAccessor`, `VulkanCommandEncoderAccessor`). Their imports and target
  annotations are version-guarded.

Stonecutter conditions are line comments. The inactive branch is kept commented out in
the source:

```java
//? if >=26.3 {
/*import com.mojang.renderpearl.frontend.FrontendGpuDevice;
*///?} else {
import com.mojang.blaze3d.systems.GpuDevice;
//?}
```

Write both branches uncommented, then run `./gradlew "Refresh active project"`. Stonecutter
comments out whichever branch is inactive and escapes nested comments for you. Never edit
the commented branch by hand while it is inactive. Switch to its version first.

Version-independent techniques used to avoid conditions:

* **Input** goes through Minecraft's `InputConstants` (`KEY_*`, `MOUSE_BUTTON_*`, `RELEASE`),
  which carry GLFW codes on 26.2 and SDL scancodes on 26.3.
* **Cursor position** is read from `MouseHandler.xpos()/ypos()` once per frame
  (`MinecraftCompat.cursorPosition()`). Minecraft's `onMove` callback changed signature in 26.3.
* **Backend detection** (`BackendClassifier`) and **GL texture names**
  (`GlTextureNames`) recognise both the Blaze3d (26.2) and Renderpearl (26.3) packages.

## What changed between 26.2 and 26.3 (for Mica)

| Area | 26.2 | 26.3 |
| ---- | ---- | ---- |
| Rendering classes | `com.mojang.blaze3d.{systems,textures,vulkan,opengl}` | `com.mojang.renderpearl.{api,frontend,backend.*}` |
| GPU device / encoder | `GpuDevice`, `CommandEncoder` (classes) | `FrontendGpuDevice`, `FrontendCommandEncoder` (`backend()` public) |
| Vulkan frame command buffer | `VulkanCommandEncoder.textureInitCommandBuffer()` | `VulkanCommandEncoder.objectInitCommandBuffer()` |
| Windowing / input | GLFW | SDL: key codes are SDL scancodes, text input must be started (`TextInputManager`) |
| `MouseHandler.onMove` | `(long, double, double)` | `(long, double, double, double, double)` |

`MixinTargetsTest` checks every mixin target against each version's Minecraft jar during
`./gradlew build`. If Mojang moves something again, the build fails and lists the target
class's actual fields and methods.

## Adding a Minecraft version

1. Add it to `versions(...)` in `settings.gradle` and create `versions/<mc>/gradle.properties`
   with `minecraft_version`, `loader_version` and `fabric_version`
   ([fabricmc.net/develop](https://fabricmc.net/develop/)).
2. Run `./gradlew "Set active project to <mc>"` and `./gradlew :<mc>:build`. Compile errors
   and `MixinTargetsTest` failures show what moved.
3. Fix them with version conditions. If the rendering internals changed substantially,
   add a new `api/compat/v<mc>/MinecraftCompatImpl_<mc>` (guarded like the existing
   adapters) and return it from `MinecraftCompat.detect()`.
4. `./gradlew "Reset active project"`, then `./gradlew build` for all versions.
