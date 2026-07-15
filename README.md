![demo](demo.gif)

GLES2 modernization proof of concept
=====================================

The `modernize/gles2-poc` branch preserves PlayLikeCurl's original three-page
model, deformation equations, projection, draw order, gesture mapping, and
300 ms settlement behavior while replacing the OpenGL ES 1 fixed-function
renderer with OpenGL ES 2 shaders and vertex/index buffers.

The modernization deliberately does not add cached ebook bitmaps, Foliate
integration, shadows, or new curl geometry. Portrait keeps the original
one-page PlayLikeCurl path. The standalone landscape sample adds only a spread
adapter around that same deformation, using four portrait leaves to demonstrate
a `1 | 2` to `3 | 4` transition.

In landscape, a forward gesture is intentionally limited to the outer edge and
the center binding:

1. Page 2 deforms toward the binding while pages 1, 2, and 4 are visible.
2. Page 3 grows from the binding over page 1 while pages 1, 3, and 4 are visible.
3. The settled spread is pages 3 and 4.

Backward navigation applies the symmetric sequence. Each leaf is rendered in a
half-width OpenGL viewport, so the deformation cannot cross the center split.

Build and test the standalone proof of concept with:

```powershell
.\gradlew.bat :karackencurllib:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
```

The reference model and source guards verify the locked page roles, geometry,
endpoints, texture order, and GLES2 boundary. Runtime verification additionally
covers forward and backward spread transitions plus the unchanged one-page
portrait path.

## Notice :warning:
This repo is no longer maintained

Introduction
============

It is an project for implementing <b>'Page Curl'</b> similar to what we see in Google Play Books using OpenGL 1.0.
The source code is released under <b>MIT License</b> can be used in commercial or personal projects.

The main motive to work on <b>'PlayLikeCurl'</b> is that its way more efficient than the traditional curl effect and is
better looking because it's way more smoother than the traditional curl.

In this project the below common sinusoidal graph equation is used to achieve the play like curl:-<br/><br/>
<b>Asin(2π/λ*x)</b><br /> 

Where,<br /> 
<b>A</b> = Amplitude (i.e. the elevation of curl we want).<br /> 
<b>λ</b> = Wavelength (i.e. the length of the curl we want).<br /> 
<b>x</b> = The X axis variable which will change as you move the page.<br /> 

In this i have drawn 3 pages on SurfaceView namely <b>'LeftPage'</b> , <b>'CenterPage'</b> and <b>'RightPage'</b>,
<b>'CenterPage'</b> is always visible and is responsible for 'right curl' animation while <b>'LeftPage'</b> is responsible
for 'left curl' animation and 'RightPage' just stay static.<br /> 

For detecting gestures the default GestureDetector class has been used.

ToDo
====
* Need to add shadow below the page when it will move.



How to Use
======================

Add Gradle dependency:

```xml
dependencies {
    compile 'com.github.karacken:karackencurllib:0.0.1'
}
```

Use Code:

```java
PageSurfaceView  pageSurfaceView = new PageSurfaceView(this);
String[] asset_res_array=null;
asset_res_array=  new String[]{"page1.png", "page2.png", "page3.png"};
PageCurlAdapter pageCurlAdapter=new PageCurlAdapter(asset_res_array);
pageSurfaceView.setPageCurlAdapter(pageCurlAdapter);
setContentView(pageSurfaceView);
```



References
======================
* Harism Page Curl [https://github.com/harism/android_page_curl]
* WikiPedia [https://en.wikipedia.org/wiki/Sine]
* OpenGL Tutorials By Google [https://developer.android.com/guide/topics/graphics/opengl.html]
