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

The `1.2.0` source checkpoint exposes production bitmap-deck API version `2`.
In addition to immutable deck leases and prepared programmatic turns, clients
can request bounded asynchronous ownership snapshots through a typed result.
The snapshot joins a stable main-thread ownership epoch with GL texture state,
and accepted requests transfer safely into terminal disposal ownership.

The `app` module provides an executable fullscreen example of the API.
The complete contract is documented in
[`karackencurllib/PRODUCTION_API.md`](karackencurllib/PRODUCTION_API.md).

## Upstream And License

Original project:
[karankalsi/PlayLikeCurl](https://github.com/karankalsi/PlayLikeCurl).

This fork remains available under the [MIT License](LICENSE.txt).
