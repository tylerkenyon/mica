# Publishing Mica — slim jar & release pipeline

This page covers what the slim library jar contains, what's left out, and how to
publish.

## Tasks

| Gradle task           | Output                                                                                |
| --------------------- | ------------------------------------------------------------------------------------- |
| `./gradlew build`     | The full project (anything you've authored drops out at this stage). Not for distribution. |
| `./gradlew libraryJar` | The slim library jar only. Lands in `build/dist-staging/mica-<version>.jar`.      |
| `./gradlew dist`       | `mica-<version>.zip` containing the slim jar + `LICENSE.txt` + `library-README.md`. |
| `./gradlew clean`      | Wipes `build/` and recovers from a stale-build trap.                                  |

`./gradlew dist` is the production-ready command. The bat shim `dist.bat` is a Windows
entrypoint for the same flow.

## What is in the slim jar

Verifiable by `unzip -l dist/mica-<version>.jar`:

| Group                                              | Entries | Notes                                  |
| -------------------------------------------------- | ------- | -------------------------------------- |
| `dev/technix/mica/api/`                            | ~15     | Public surface (`OverlayRenderer`, `OverlayElement`, `RenderContext`, `MicaScreen`, `Palette`, `FontRegistry`, `FontFace`, `FrostedGlassStyle`, `SpriteBounds`, `TextureFilter`, `TextureHandle`, `VanillaAtlases`, ...). |
| `dev/technix/mica/api/compat/v26_2/`              | ~3      | `MinecraftCompatImpl_26_2` and its support classes.   |
| `dev/technix/mica/internal/`                       | ~25     | Renderer, Vulkan backend, screen detector, input router, font atlas loader, active-renderer registry. |
| `dev/technix/mica/internal/util/`                  | ~3      | `Draw`, `Theme`.                       |
| `dev/technix/mica/internal/backend/vulkan/`        | ~6      | `FrostedGlassRenderer`, `VulkanContext`, `VulkanImGuiBackend`, `VulkanShaderCompiler`. |
| `dev/technix/mica/examples/`                       | ~2      | `ToastElement`.                        |
| `dev/technix/mica/mixin/client/`                   | ~6      | Mixin accessors required by the 26.2 adapter. |
| `assets/mica/`                                     | fonts  | SF Pro Display is bundled (read the licence note in the README before commercial use). |
| `META-INF/jars/`                                   | jar-in-jar | imgui-java 1.92 + LWJGL Vulkan bits, packaged so the consumer has no extra dependencies. |

`fabric.mod.json` is **not** in the jar. A host's Fabric Loader treats the artefact as
plain library code, not a conflicting mod entry, when dropped into `/libs/`.

## What is not in the slim jar

Anything you've authored locally — your in-tree HUDs, tests, samples under your own
package, anything that is not part of `dev.technix.mica.*` — gets stripped before
packaging. The `libraryJar` task's `exclude` pattern catches everything outside
`dev/technix/mica/**` and the `assets/mica/**` directory.

If you want your own HUDs in a separate slim jar (e.g. you ship a public pack of HUDs
on top of Mica), that's a separate Gradle task — out of scope here.

## Consumer install

The consumer side is documented in [`setup.md`](./setup.md). In short:

1. Drop the jar into `libs/`.
2. Wire `implementation files("libs/mica-<version>.jar")` in their `build.gradle`.
3. Declare a `custom` key in their `fabric.mod.json` (Mica doesn't read this; it's
   for end-user attribution).

## CI / release pipeline

`.github/workflows/build-and-release.yml` runs on every push to `main` and on every
PR against `main`. The flow:

1. **Build verify (`build` job).** Compiles + packages the full project. PRs from
   forks skip the slim-jar step to avoid fetching the Minecraft jar artefact.
2. **Dev pre-release (`release-dev` job) — only on push to `main`.** Computes
   `dev-<short-sha>` as the tag (e.g. `dev-a1b2c3d`), marks `prerelease: true`, runs
   `./gradlew dist`, and uploads the resulting zip as a GitHub pre-release.
3. **Full release — only on tag matching `v*` (e.g. `v0.2.0`).** Same as above with
   `prerelease: false`.

Concurrency: a push twice in quick succession on main cancels the older CI; PR runs
have independent concurrency keys. Failures: any missing `dist/*.zip` causes the
release to fail rather than publish an empty release.

### To cut a release

```sh
# 1. Bump mod_version in gradle.properties if you want this to ship as a new version.
#    Leave as-is if you only want a new dev pre-release.

# 2. Tag the commit:
git tag v0.2.0
git push origin v0.2.0

# 3. The workflow runs the full release path and publishes.
```

### To cut a dev pre-release

Every push to `main` automatically runs the dev pre-release path. No action needed;
the workflow creates a `dev-a1b2c3d` pre-release in `Releases`.

If a push *fails* to publish the pre-release (e.g. CI crashed), re-run the workflow
from the GitHub Actions UI.

## Versioning

Mica uses `gradle.properties` field `mod_version`:

```sh
mod_version=0.1
```

Bump it on `v*` tags. Dev pre-releases inherit the current `mod_version` from
`gradle.properties` at push time; the `dev-<sha>` suffix is added by the workflow
only.

## What happens if the workflow file changes

PRs to `build-and-release.yml` follow the same flow as code PRs — the build verify
runs, and the merge to main creates a dev pre-release. Test a workflow change by
pushing to a branch on a fork first.

## Reading more

* [`setup.md`](./setup.md) — consumer install.
* [`vulkan.md`](./vulkan.md) — the version adapter that the slim jar carries.
* [`api.md`](./api.md) — what the slim jar's public surface looks like.
