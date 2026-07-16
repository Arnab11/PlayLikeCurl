# PlayLikeCurl

An Android page-turn renderer inspired by the interaction used in Google Play
Books. This maintained fork modernizes the original PlayLikeCurl project from
OpenGL ES 1.0 to OpenGL ES 2.0 while preserving its page model, deformation
behavior, gesture mapping, and settlement rules.

![PlayLikeCurl demo](demo.gif)

## Features

- OpenGL ES 2.0 shaders and vertex/index buffers.
- Bidirectional portrait page turns using the original three-page model.
- Dual-page landscape transitions bounded by the center binding.
- Fold cast shadow that follows the deformation edge.
- Drag, fling, commit, and cancel settlement behavior.
- Standalone Android sample application.
- JVM tests and source guards for geometry, page roles, texture order, and the
  GLES2 boundary.

## How It Works

PlayLikeCurl deforms the active page using a sinusoidal profile:

```text
A * sin((2 * PI / wavelength) * x)
```

Where:

- `A` controls the curl elevation.
- `wavelength` controls the curl width.
- `x` is the horizontal page position.

Portrait mode retains the original left, center, and right page roles.
Landscape mode applies the same deformation to individual half-width leaves so
the animation cannot cross the center binding.

## Repository Layout

- `karackencurllib/` - reusable Android library.
- `app/` - standalone demonstration application.

The library currently accepts page image names through `PageCurlAdapter`. A
client-provided bitmap/deck API is planned before the first stable library
release.

## Build

Requirements:

- JDK 17 or newer.
- Android SDK with API 37 installed.

Build the library, run the unit tests, and assemble the sample:

```powershell
.\gradlew.bat :karackencurllib:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
```

The sample APK is generated under:

```text
app/build/outputs/apk/debug/
```

## Use From Source

Include both modules in the consuming Gradle build:

```groovy
include ':app', ':karackencurllib'
```

Add the library project as a dependency:

```groovy
dependencies {
    implementation project(':karackencurllib')
}
```

Create the view and provide page images stored in the application's assets:

```java
import karacken.curl.PageCurlAdapter;
import karacken.curl.PageSurfaceView;

PageSurfaceView pageSurfaceView = new PageSurfaceView(this);
PageCurlAdapter adapter = new PageCurlAdapter(
        new String[]{"page1.png", "page2.png", "page3.png"}
);
pageSurfaceView.setPageCurlAdapter(adapter);
setContentView(pageSurfaceView);
```

No Maven or JitPack artifact is currently published by this fork. Use a pinned
source revision until a stable public API and release tag are available.

## Project Status

The GLES2 modernization and standalone portrait/landscape demonstrations are
working and covered by tests. The next compatibility boundary is a bounded
client bitmap API with explicit page identity, lifecycle, settlement, and
failure contracts.

## Credits

This fork is based on
[karankalsi/PlayLikeCurl](https://github.com/karankalsi/PlayLikeCurl).

References used by the original project:

- [Harism Android Page Curl](https://github.com/harism/android_page_curl)
- [Sine wave](https://en.wikipedia.org/wiki/Sine_wave)
- [Android OpenGL ES documentation](https://developer.android.com/develop/ui/views/graphics/opengl)

## License

PlayLikeCurl is available under the [MIT License](LICENSE.txt).
