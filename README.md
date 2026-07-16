# PlayLikeCurl

PlayLikeCurl is a maintained Android library for deforming pre-rendered pages
with a Google Play Books-style turn animation.

This fork modernizes
[karankalsi/PlayLikeCurl](https://github.com/karankalsi/PlayLikeCurl) from
OpenGL ES 1.0 to OpenGL ES 2.0 while preserving its page model and deformation
behavior.

## Scope

PlayLikeCurl owns:

- page-turn geometry and rendering
- drag, fling, commit, and cancel settlement
- forward and backward portrait turns
- dual-page landscape turns bounded by the center binding
- fold shadows
- GPU texture validation and lifecycle

The consuming reader owns:

- document parsing and pagination
- page preparation and caching
- page identity and navigation state
- overlays such as highlights or annotations
- reader settings and persistence

The library does not parse EPUB, PDF, or comic formats and does not fetch or
cache document content.

## Fork Status

- OpenGL ES 2.0 shaders and buffer-backed rendering
- Android 7.0 (API 24) minimum
- portrait and landscape page-deck models
- tests for the reference geometry, page roles, texture order, and settlement

The `app` module is the executable reference for the current source API. The
public integration contract will be documented here when it is released rather
than exposing internal demo paths as a stable client API.

## Build

The repository contains:

- `karackencurllib`: reusable Android library
- `app`: standalone visual demo

Run the library tests and build the demo:

```powershell
.\gradlew.bat :karackencurllib:testDebugUnitTest :app:assembleDebug
```

JDK 17 and Android SDK API 37 are required to build the current checkout.

## Distribution

No Maven or JitPack artifact is published. Embed the library from source and pin
the exact commit used by the consuming application.

## License

PlayLikeCurl remains available under the [MIT License](LICENSE.txt).
