package karacken.curleffect;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class ProductionDemoSourceTest {
    @Test
    public void demoWaitsForCapabilitiesAndHandlesFailuresWithoutThrowing()
            throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/karacken/curleffect/MainActivity.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("onCapabilitiesAvailable"));
        assertTrue(source.contains("nextGenerationId"));
        assertTrue(source.contains("Bitmap.Config.ARGB_8888"));
        assertTrue(source.contains("setHasAlpha(false)"));
        assertFalse(source.contains("throw new IllegalStateException(failure.getMessage()"));
    }
}
