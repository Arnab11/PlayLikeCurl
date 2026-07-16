# PlayLikeCurl

PlayLikeCurl is an Android page-turn rendering library. This maintained fork
ports the original project from OpenGL ES 1.0 to OpenGL ES 2.0 and extends the
demo with bidirectional portrait turns, dual-page landscape spreads, and fold
shadows.

![PlayLikeCurl demo](demo.gif)

## Changes In This Fork

- OpenGL ES 2.0 shaders and buffer-backed rendering.
- Forward and backward page turns.
- Drag, fling, commit, and cancel settlement.
- Portrait rendering with previous, current, and next pages.
- Landscape rendering with previous, current, and next two-page spreads.
- Landscape turns constrained to one leaf and the center binding.
- A cast shadow that follows the fold.
- Tests for geometry, page roles, texture order, settlement, and GLES2 usage.

## Requirements

- Android 7.0 (API 24) or newer.
- JDK 17 or newer.
- Android SDK API 37 to build the current project.

## Repository

- `karackencurllib/` contains the reusable Android library.
- `app/` contains the standalone demo.
- `app/src/main/assets/portrait/` contains portrait sample pages.
- `app/src/main/assets/landscape/` contains landscape sample pages.

## Build And Run

Run the library tests and assemble the demo APK:

```powershell
.\gradlew.bat :karackencurllib:testDebugUnitTest :app:assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Use From Source

Include the library module in the consuming Gradle build:

```groovy
include ':karackencurllib'
```

Add the project dependency:

```groovy
dependencies {
    implementation project(':karackencurllib')
}
```

The API currently published on `master` reads page images from the consuming
application's assets:

```java
import karacken.curl.PageCurlAdapter;
import karacken.curl.PageSurfaceView;

PageSurfaceView pageView = new PageSurfaceView(this);
pageView.setLandscapeSpreadEnabled(false);
pageView.setPageCurlAdapter(new PageCurlAdapter(new String[] {
        "portrait/page1.png",
        "portrait/page2.png",
        "portrait/page3.png"
}));
pageView.setOnPageChangeListener(position -> {
    // Persist or display the new zero-based page position.
});

setContentView(pageView);
```

Set `setLandscapeSpreadEnabled(true)` before installing the adapter to use the
dual-page landscape model.

No Maven or JitPack artifact is currently published. Pin a source revision when
embedding the library.

## Upstream And License

This repository is a maintained fork of
[karankalsi/PlayLikeCurl](https://github.com/karankalsi/PlayLikeCurl).

The project is available under the [MIT License](LICENSE.txt).
