# PlayLikeCurl

PlayLikeCurl is an Android page-turn renderer with a Google Play Books-style
deformation effect.

This maintained fork of
[karankalsi/PlayLikeCurl](https://github.com/karankalsi/PlayLikeCurl) preserves
the original page model and interaction while replacing the legacy OpenGL ES
1.0 renderer with OpenGL ES 2.0.

## Fork Changes

- OpenGL ES 2.0 shaders and buffer-backed rendering
- forward and backward page turns
- drag, fling, commit, and cancel settlement
- portrait page rendering
- dual-page landscape rendering bounded by the center binding
- fold cast shadows
- Android 7.0 (API 24) minimum
- automated geometry, page-role, texture-order, and settlement tests

## Modules

- `karackencurllib`: reusable page-turn library
- `app`: standalone visual demo

## Build

Requirements:

- JDK 17
- Android SDK API 37

Run the library tests and build the demo:

```powershell
.\gradlew.bat :karackencurllib:testDebugUnitTest :app:assembleDebug
```

The demo APK is written under `app/build/outputs/apk/debug/`.

## Using The Library

No Maven or JitPack artifact is currently published. Include
`karackencurllib` from source and pin the exact fork commit used by the
application.

Release `1.1.3` exposes production bitmap-deck API version `1`. The client
prepares immutable page bitmaps, submits a complete portrait or landscape
interaction window, and retains each accepted bitmap until the matching
deck-release callback. Prepared edge taps can start the same reference
settlement as a completed drag through `PageSurfaceView.turn(PageChange)`.

The `app` module provides an executable fullscreen example of that API.
The complete contract is documented in
[`karackencurllib/PRODUCTION_API.md`](karackencurllib/PRODUCTION_API.md).

## Upstream And License

Original project:
[karankalsi/PlayLikeCurl](https://github.com/karankalsi/PlayLikeCurl).

This fork remains available under the [MIT License](LICENSE.txt).
