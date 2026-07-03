# Phoenix Studio

A mobile-first, open-source game engine and editor built to run **on** an
Android phone, not just for one — the whole point is authoring games from
the device itself.

This is bootstrap round 1. It is not a game; it is the foundation of the
editor application.

## What exists right now

| Module     | Status | Contents |
|------------|--------|----------|
| `:core`    | ✅ done | `Vec3`, `Mat4`, `Quaternion` math; `Logger` facade |
| `:renderer`| ✅ done | GLES 3.2 `GLSurfaceView`, shader/mesh/camera code, cube + grid, orbit/pan/zoom touch, FPS counter |
| `:app`     | ✅ done | Full-screen viewport shell (`MainActivity`) — the eventual editor host |
| `:scene`, `:ui`, `:assets`, `:project`, `:plugins`, `:filesystem`, `:editor` | ⏳ not started | Added in later rounds |

Run it today and you get: a dark full-screen 3D viewport with a lit orange
cube on a reference grid, an FPS counter top-right, and touch-driven orbit
(1 finger), pan (2 finger drag), and pinch-zoom camera control.

## Building locally

Requires JDK 17 and Android Studio (Koala+) or a standalone Gradle 8.9 install.

```bash
gradle :app:assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

There is **no committed Gradle wrapper jar** in this repo (see
`settings.gradle.kts`) — install Gradle 8.9 locally, or open the project in
Android Studio, which bootstraps its own Gradle.

## CI (GitHub Actions, free-tier minutes)

`.github/workflows/build.yml` has two jobs:

- **`build-debug`** — runs on every push to `main`, needs **no secrets at
  all**. Android's build tools auto-generate a debug signing key on the
  CI runner, so the resulting APK is installable on your phone with zero
  setup. **Use this one until you're ready for a real release.**
- **`build-release`** — skipped by default (`if: vars.RELEASE_SIGNING_READY
  == 'true'`) until you've added the four `PHOENIX_*` signing secrets below
  and set that repo variable to `true`. This avoids burning CI minutes on a
  release build you can't sign yet.

Getting the debug APK: **Actions tab → latest run → `phoenix-studio-debug-apk`
artifact → download → unzip → install.**

### Setting up release signing (optional, do this later)

Generating a real signing keystore requires a JDK's `keytool`, which isn't
available on a phone without Termux or a computer. If you're phone-only,
skip this section for now and ship debug builds; come back to it once you
have access to any machine with a JDK (a friend's laptop, a free cloud
shell, etc.).

1. Generate a keystore once:
   ```bash
   keytool -genkeypair -v -keystore release.keystore \
     -alias phoenix -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Base64-encode it and add these four repo secrets under
   **Settings → Secrets and variables → Actions → Secrets**:
   - `PHOENIX_KEYSTORE_BASE64` — output of `base64 -w0 release.keystore`
   - `PHOENIX_KEYSTORE_PASSWORD`
   - `PHOENIX_KEY_ALIAS` — `phoenix` if you used the command above
   - `PHOENIX_KEY_PASSWORD`
3. Under **Settings → Secrets and variables → Actions → Variables**, add a
   repo variable `RELEASE_SIGNING_READY` = `true` to turn the release job on.
4. Push to `main` (or run the workflow manually). The signed APK is
   uploaded as the `phoenix-studio-release-apk` build artifact.

**Never commit `release.keystore` itself** — `.gitignore` already excludes
`*.keystore`/`*.jks`.

## Architecture

Clean-architecture module boundaries, each independently buildable:

```
app/        installable shell — will host editor chrome once :ui exists
core/       math, logging — zero Android View/GL dependencies
renderer/   GLSurfaceView, GLES 3.2, camera, meshes — depends only on core
scene/      (planned) scene graph, transforms, JSON save/load
ui/         (planned) explorer / inspector / console / toolbar panels
assets/     (planned) asset browser, thumbnails, import pipeline
project/    (planned) project format, Projects/ directory management
plugins/    (planned) plugin interface + loader
filesystem/ (planned) Assets/Scenes/Textures/Models/Scripts/Plugins/Autosaves/Logs layout
editor/     (planned) wires ui + scene + assets + project into the full editor
```

Only real, compiling code is committed — no empty stub modules, no
placeholder classes. Each subsequent bootstrap round adds one or two
modules end-to-end.

## Target device baseline

Redmi 9A · 2GB RAM · Android 10 · OpenGL ES 3.2 — every rendering and
memory decision (VAO-based static mesh upload, no per-frame allocation in
the render loop or FPS counter, `minSdk 29`) is made against this floor.
