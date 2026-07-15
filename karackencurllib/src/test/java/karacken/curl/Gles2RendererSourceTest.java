package karacken.curl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class Gles2RendererSourceTest {
    @Test
    public void rendererUsesShadersWithoutFixedFunctionCalls() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/karacken/curl/PageRenderer.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("GLES20"));
        assertTrue(source.contains("glCreateShader"));
        assertTrue(source.contains("glUseProgram"));
        assertFalse(source.contains("GLU.gluPerspective"));
        assertFalse(source.contains("glMatrixMode"));
        assertFalse(source.contains("glVertexPointer"));
        assertFalse(source.contains("glTexCoordPointer"));
        assertFalse(source.contains("glEnableClientState"));
    }

    @Test
    public void surfaceRequestsAnOpenGlEs2ContextBeforeInstallingRenderer() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/karacken/curl/PageSurfaceView.java"),
                StandardCharsets.UTF_8);

        int contextRequest = source.indexOf("setEGLContextClientVersion(2)");
        int rendererInstall = source.indexOf("setRenderer(renderer)");
        assertTrue(contextRequest >= 0);
        assertTrue(rendererInstall > contextRequest);
    }

    @Test
    public void landscapeUsesTwoLeafViewportsWithoutChangingPortraitComposition() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/karacken/curl/PageRenderer.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("drawLandscapeSpread"));
        assertTrue(source.contains("viewportWidth / 2"));
        assertTrue(source.contains("LandscapeSpreadTransition"));
        assertTrue(source.contains("spreadNextLeftResource"));
        assertTrue(source.contains("mirroredLeftMesh"));
        assertTrue(source.contains("mirroredFrontMesh"));
        assertTrue(source.contains("drawPortraitPage"));
        assertTrue(source.contains("GLES20.glViewport(0, 0, viewportWidth, viewportHeight)"));
    }
}
