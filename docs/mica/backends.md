# Rendering backends: OpenGL and Vulkan

Minecraft 26.2 can render with **Vulkan** or **OpenGL** (`--graphicsBackend`, or the
video settings). Mica supports both through one backend-independent API: the same mod
jar, the same `Mica` / `OverlayRenderer` / `OverlayElement` code, on either backend.

## For mod developers: nothing to do

```java
Mica mica = Mica.create();

mica.registerOverlay(ctx -> {
    ImGui.begin("My Mod");
    ImGui.text("Hello Minecraft!");
    if (ImGui.button("Click me")) {
        // ...
    }
    ImGui.end();
});
```

* You never import, reference or branch on Vulkan, OpenGL, GLFW, `VkCommandBuffer`,
  `VkImageView`, descriptor sets, GL texture names or Mojang's `com.mojang.blaze3d.*`
  classes.
* The backend is detected automatically the first time Minecraft draws a frame.
  There is no `Mica.create(RenderBackend.OPENGL)` and no `isVulkan()` check.
* `mica.backend()` / `OverlayRenderer.activeBackend()` return a
  `RenderBackendType` (`VULKAN`, `OPENGL`, `UNKNOWN`) for diagnostics only.

### Textures

Textures are an opaque `MicaTexture`:

```java
MicaTexture items = mica.texture(VanillaAtlases.ITEMS, TextureFilter.NEAREST);

mica.registerOverlay(ctx -> {
    long id = items.imGuiTextureId();   // 0 while not available
    if (id != 0) {
        ImGui.image(id, 64, 64);        // or Draw.image(ctx, items, x, y, w, h)
    }
});

// when done (any thread):
items.close();
```

* `imGuiTextureId()` is Dear ImGui's own opaque `ImTextureID`. Pass it to ImGui
  only. On Vulkan it maps to a descriptor set and on OpenGL to a Mica-side
  (texture, sampler) entry, but that is an internal detail and differs per backend.
* **Lifetime:** the texture belongs to the renderer that created it. It follows
  Minecraft re-creating the image (resource reloads, resizes) automatically. Handles
  for the same texture and filter share one GPU registration (ref-counted).
  `close()` drops your reference. The last close releases the registration a few
  frames later, once no frame in flight can still sample it.
* **Threading:** read the id inside `render(...)` (the render thread). `close()` is
  safe from any thread; the GPU release is deferred to the render thread.
* While the renderer is shut down or not yet initialised, the id is `0` and
  `Draw.image(...)` draws nothing. Unclosed textures work again after a restart.

The older `registerAtlasTexture(Identifier, TextureFilter)` (returns a per-frame
`TextureHandle`, which now also implements `MicaTexture`) still works on both
backends. `registerRawTexture(long imageView, int layout, filter)` is
**deprecated**: it takes a raw Vulkan image view, works only on Vulkan and returns `0`
elsewhere.

### Threading

All GPU work happens on Minecraft's render thread, driven by Mica's
`GuiRenderer.render()` mixin. Overlays render there and only there.
`registerOverlay` / `unregisterOverlay` / `MicaTexture.close()` are safe from any
thread. A frame requested off the render thread is refused (one WARN), never executed.

## Feature matrix

| Feature                          | Vulkan | OpenGL | Notes |
| -------------------------------- | :----: | :----: | ----- |
| ImGui windows, widgets, text     | ✔ | ✔ | Same Dear ImGui frame; only the final draw differs. |
| Mouse / keyboard input           | ✔ | ✔ | Backend-independent (`ImGuiInputRouter`), shared code. |
| `OverlayElement` + `MicaScreen` scopes | ✔ | ✔ | |
| Bundled + custom fonts           | ✔ | ✔ | |
| `MicaTexture` (atlases, textures)| ✔ | ✔ | `TextureFilter.NEAREST` / `LINEAR` honoured on both. |
| Frosted glass (`Draw.frostedPanel`) | ✔ | ✔ | Vulkan: compute shader. OpenGL: fragment-shader port of the same Kawase kernel. |
| `registerRawTexture` (deprecated)| ✔ | ✖ | Takes a Vulkan image view by design; returns `0` on OpenGL. |

If frosted glass fails to initialise on either backend (driver quirk, missing
feature), Mica logs one WARN and keeps rendering. `Draw.frostedPanel` then draws
the tint and border without the blurred backdrop (`RenderContext.hasBlur()` is
`false`). The rest of the overlay is unaffected.

### Remaining differences

* **Frosted glass timing.** On Vulkan the blur is recorded into Minecraft's frame
  command buffer. On OpenGL it runs immediately with a `glBlitFramebuffer`
  downsample plus N fragment passes. Both use the same kernel and scale divisor.
  One small difference: with an even `blurPasses`, the Vulkan path samples the
  second-to-last ping-pong target (its existing behaviour, left unchanged), while
  OpenGL samples the final pass.
* **Font atlas upload.** Vulkan records the upload into the first frame's command
  buffer (the overlay appears one frame after initialisation). OpenGL uploads
  synchronously.
* **Raw textures.** See the deprecated `registerRawTexture` above.

## Errors

Backend start-up never throws into Minecraft. If Mica cannot start a renderer it logs
one ERROR and disables the overlay for the session. The message always names the
Minecraft version, the detected rendering backend, the Mica version and the reason:

```
Mica could not start its renderer (Minecraft 26.2, rendering backend Unknown, Mica 0.1).
Reason: Mica has no renderer for the Unknown backend (supported: [VULKAN, OPENGL]).
The overlay is disabled for this session.
```

The same exception (`MicaBackendException`) is available from
`OverlayRenderer.failure()`. Mica never falls back to a different backend than the one
Minecraft is using.

## How detection works (contributors)

1. `MinecraftCompat.renderBackend()` (26.2 implementation in
   `MinecraftCompatImpl_26_2`) reads `RenderSystem.tryGetDevice()` and, through the
   `GpuDeviceAccessor` mixin, the device's `GpuDeviceBackend`.
2. `VulkanDevice` → `VULKAN`. Otherwise the backend class is classified by package
   (`BackendClassifier`): `com.mojang.blaze3d.opengl.*` → `OPENGL`, anything else
   → `UNKNOWN`. The package check exists because `GlDevice` is package-private
   since 26.1 and cannot be used in an `instanceof`.
3. No device yet (very early start-up) → empty; the core simply retries next frame.
4. On the first frame with a device, `ImGuiRenderer` asks the
   `RenderBackendRegistry` for the matching factory and creates the backend once.

## Architecture (contributors)

```
                     Mica public API  (api/)
     Mica · OverlayRenderer · OverlayElement · MicaOverlay · RenderContext
     MicaTexture · RenderBackendType · MicaBackendException · MinecraftCompat
                              │
                              ▼
                 Mica core  (internal/ImGuiRenderer)
   ImGui context · font atlas · frame lifecycle · TextureCache · input routing
                              │  RenderBackend (internal/backend/)
               ┌──────────────┴──────────────┐
               ▼                             ▼
     VulkanRenderBackend              OpenGLRenderBackend
     ├ VulkanImGuiBackend             ├ OpenGLImGuiBackend
     └ FrostedGlassRenderer           └ OpenGLFrostedGlass
               │ VulkanHostAccess            │ OpenGLHostAccess
               └──────────────┬──────────────┘
                              ▼
            MinecraftCompatImpl_26_2  (api/compat/v26_2)
                 Minecraft 26.2 rendering classes
```

* **Core code never imports Vulkan or OpenGL.** `ApiBoundaryTest` fails the build if
  `api/` (outside `compat/`) or the core references `org.lwjgl.vulkan`,
  `org.lwjgl.opengl`, `com.mojang.blaze3d.vulkan/opengl` or the backend packages.
* `RenderBackend` is the whole contract: `initialize`, `beginFrame`, `prepareFrame`,
  `recordBackdrop`, `render(ImDrawData)`, texture registration, frosted-glass
  configuration and `shutdown`. The core owns everything else, so frame management
  is not duplicated per backend.
* Host access is split per backend: `VulkanHostAccess` (device, frame command buffer,
  image views) and `OpenGLHostAccess` (main render target, texture names). The compat
  adapter implements both, and the backends obtain theirs through
  `MinecraftCompat.backendAccess(Class)`, so no backend type appears in the public
  interface.

### OpenGL backend details

* **No new context, no new window.** Every call runs on Minecraft's render thread, where
  Minecraft's own GL context is current (`GL.getCapabilities()` is that context's).
* **Target.** Mica draws into a framebuffer object it owns, with Minecraft's main
  render target colour texture as its only attachment. It is re-attached every frame,
  so resizes and re-created textures are picked up, and Mica never depends on which
  framebuffer Minecraft left bound or touches Minecraft's framebuffer cache.
* **State.** `GlStateSnapshot` captures every piece of GL state Mica touches with
  `glGet*` and restores it exactly: program, VAO, buffers, texture unit and sampler,
  draw/read framebuffers, viewport, scissor, blend (per draw buffer), colour/depth
  masks, cull/depth/stencil/sRGB/primitive restart, polygon mode and unpack state.
  Minecraft's `GlStateManager` cache therefore stays truthful.
* **Renderer.** `OpenGLImGuiBackend` follows imgui-java's `ImGuiImplGl3` (Dear ImGui's
  `imgui_impl_opengl3`) on GLSL 150 core, with one change: every ImGui texture id
  carries its own sampler object. Minecraft 26.x samples through sampler objects,
  and `ImGuiImplGl3` binds sampler 0, which would sample atlases with whatever
  per-texture parameters they happen to have and could not honour `TextureFilter`.
  Keeping it in-tree also avoids the `imgui-java-lwjgl3` artifact, whose POM pulls
  a second set of LWJGL modules.
* **Texture names.** The compat adapter reads `GlTexture`'s GL name reflectively
  (`glId()`, falling back to the `id` field). A missing name disables only the OpenGL
  overlay, with a log line saying so. A mixin accessor would instead fail mixin
  application for every backend.

### Vulkan backend details

The existing Vulkan renderer is unchanged in behaviour. `VulkanImGuiBackend` (the
dynamic-rendering pipeline that records into Minecraft's command buffer) and
`FrostedGlassRenderer` (compute Kawase blur) are the same classes as before.
`VulkanRenderBackend` adapts them to `RenderBackend`, taking over the code that used
to live in `ImGuiRenderer`: context refresh, HUD samplers, pending font upload,
blur recording, own-pass render. See [`vulkan.md`](./vulkan.md).

## Adding a backend

1. Implement `RenderBackend` in a new package `internal/backend/<name>/`. Keep all
   API-specific imports there.
2. Define the host access it needs as an interface in that package (as
   `VulkanHostAccess` / `OpenGLHostAccess` do) and implement it in each
   `MinecraftCompatImpl_*` that supports the backend.
3. Add a constant to `RenderBackendType`, teach the compat adapter's `renderBackend()`
   (or `BackendClassifier`) to report it, and register the factory in
   `RenderBackends.defaultRegistry()`.
4. Nothing in `api/`, `ImGuiRenderer`, `OverlayRenderer`, input or texture handling
   changes. Run `./gradlew test`; `ApiBoundaryTest` checks the boundary.

## Testing a specific backend in dev

```
./gradlew runClient -PmicaBackend=vulkan           # force Vulkan
./gradlew runClient -PmicaBackend=opengl           # force OpenGL
./gradlew runClient -PmicaDemoWindow               # add a plain ImGui demo window
```

`-PmicaBackend` is passed through as Minecraft's `--graphicsBackend` argument.
Without it, Minecraft picks its own backend and Mica follows.
