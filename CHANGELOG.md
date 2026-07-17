# Changelog

## 1.1.2 - 2026-07-16

- Added `PageSurfaceView.turn(PageChange)` for prepared programmatic edge-tap
  turns.
- Reused the reference forward and backward settlement paths without adding
  separate geometry or timing behavior.
- Rejected programmatic turns when the deck is unavailable, settlement is
  active, or the requested direction is outside the current boundary.

## 1.1.1 - 2026-07-16

- Made the reusable library's tests self-contained when the module is vendored
  into another Gradle build.
- Moved the standalone-demo source assertion to the demo module.
- Added module-local production bitmap API documentation.

## 1.1.0 - 2026-07-16

- Added the versioned production API for client-prepared bitmap page decks.
- Defined the bitmap lease: accepted bitmaps remain immutable and available until
  the corresponding deck-release callback.
- Added structured capability, rejection, settlement, and rendering callbacks on
  the Android main thread.
- Defined accepted Android images precisely: opaque ARGB_8888 base pages and
  optional premultiplied ARGB_8888 overlays with alpha.
- Revalidate retained decks after OpenGL context recreation and release only decks
  that can no longer satisfy the current texture limits.
- Added a compile-only external consumer module so the public API is verified
  outside the library package.
