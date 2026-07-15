package karacken.curl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** GLES2 renderer for PlayLikeCurl's original three-page model and deformation. */
public final class PageRenderer implements GLSurfaceView.Renderer {
    private static final String VERTEX_SHADER =
            "uniform mat4 uMvpMatrix;\n"
                    + "attribute vec3 aPosition;\n"
                    + "attribute vec2 aTextureCoordinate;\n"
                    + "varying vec2 vTextureCoordinate;\n"
                    + "void main() {\n"
                    + "  gl_Position = uMvpMatrix * vec4(aPosition, 1.0);\n"
                    + "  vTextureCoordinate = aTextureCoordinate;\n"
                    + "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n"
                    + "uniform sampler2D uTexture;\n"
                    + "varying vec2 vTextureCoordinate;\n"
                    + "void main() {\n"
                    + "  gl_FragColor = texture2D(uTexture, vTextureCoordinate);\n"
                    + "}\n";

    private final Context context;
    private final GpuPage leftPage = new GpuPage(PageRole.LEFT);
    private final GpuPage frontPage = new GpuPage(PageRole.FRONT);
    private final GpuPage rightPage = new GpuPage(PageRole.RIGHT);
    private final float[] projectionMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    private PlayLikeCurlModel model;
    private PageOrientation orientation = PageOrientation.PORTRAIT;
    private int program;
    private int positionAttribute;
    private int textureCoordinateAttribute;
    private int matrixUniform;
    private int textureUniform;

    public PageRenderer(Context context) {
        this.context = context.getApplicationContext();
    }

    void setModel(PlayLikeCurlModel model) {
        this.model = model;
    }

    public void updatePageRes(String leftResource, String frontResource, String rightResource) {
        leftPage.setAssetPath(leftResource);
        frontPage.setAssetPath(frontResource);
        rightPage.setAssetPath(rightResource);
    }

    @Override
    public void onSurfaceCreated(GL10 ignored, EGLConfig config) {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        positionAttribute = GLES20.glGetAttribLocation(program, "aPosition");
        textureCoordinateAttribute = GLES20.glGetAttribLocation(program, "aTextureCoordinate");
        matrixUniform = GLES20.glGetUniformLocation(program, "uMvpMatrix");
        textureUniform = GLES20.glGetUniformLocation(program, "uTexture");

        GLES20.glClearColor(0f, 0f, 0f, 0.5f);
        GLES20.glClearDepthf(1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);

        leftPage.initializeGl();
        frontPage.initializeGl();
        rightPage.initializeGl();
    }

    @Override
    public void onSurfaceChanged(GL10 ignored, int width, int height) {
        int safeHeight = Math.max(height, 1);
        int safeWidth = Math.max(width, 1);
        GLES20.glViewport(0, 0, safeWidth, safeHeight);
        orientation = safeHeight > safeWidth
                ? PageOrientation.PORTRAIT
                : PageOrientation.LANDSCAPE;
        Matrix.perspectiveM(
                projectionMatrix,
                0,
                45f,
                PlayLikeCurlGeometry.projectionAspect(safeWidth, safeHeight),
                0.1f,
                100f);
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, 0f, 0f, -2f);
        Matrix.translateM(modelMatrix, 0, -0.5f, -0.5f, 0f);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0);

        leftPage.invalidateAsset();
        frontPage.invalidateAsset();
        rightPage.invalidateAsset();
    }

    @Override
    public void onDrawFrame(GL10 ignored) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        PlayLikeCurlModel currentModel = model;
        if (currentModel == null) return;

        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(matrixUniform, 1, false, mvpMatrix, 0);
        GLES20.glUniform1i(textureUniform, 0);

        drawPage(leftPage, currentModel.getLeftPage(), currentModel.getActivePage() == ActivePage.LEFT);
        drawPage(frontPage, currentModel.getFrontPage(), currentModel.getActivePage() == ActivePage.CURRENT);
        drawPage(rightPage, currentModel.getRightPage(), currentModel.getActivePage() == ActivePage.RIGHT);
    }

    private void drawPage(GpuPage page, PageState state, boolean active) {
        if (!page.ensureAsset(orientation)) return;
        PlayLikeCurlGeometry.update(page.geometry, state.getCurlPosition(), active);
        page.uploadPositions();

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, page.textureId);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, page.positionBufferId);
        GLES20.glEnableVertexAttribArray(positionAttribute);
        GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, page.textureBufferId);
        GLES20.glEnableVertexAttribArray(textureCoordinateAttribute);
        GLES20.glVertexAttribPointer(textureCoordinateAttribute, 2, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, page.indexBufferId);
        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                page.geometry.getIndices().length,
                GLES20.GL_UNSIGNED_SHORT,
                0);
        GLES20.glDisableVertexAttribArray(positionAttribute);
        GLES20.glDisableVertexAttribArray(textureCoordinateAttribute);
    }

    private final class GpuPage {
        private final PageRole role;
        private final FloatBuffer positionBuffer;
        private final FloatBuffer textureBuffer;
        private final ShortBuffer indexBuffer;
        private final int[] bufferIds = new int[3];
        private final int[] textureIds = new int[1];

        private PageGeometry geometry;
        private volatile String assetPath = "";
        private String uploadedAssetPath;
        private PageOrientation uploadedOrientation;
        private int positionBufferId;
        private int textureBufferId;
        private int indexBufferId;
        private int textureId;

        GpuPage(PageRole role) {
            this.role = role;
            geometry = PlayLikeCurlGeometry.createPage(
                    role, 1, 1, PageOrientation.PORTRAIT);
            positionBuffer = directFloatBuffer(geometry.getPositions().length);
            textureBuffer = directFloatBuffer(geometry.getTextureCoordinates().length);
            indexBuffer = directShortBuffer(geometry.getIndices().length);
        }

        void initializeGl() {
            GLES20.glGenBuffers(bufferIds.length, bufferIds, 0);
            positionBufferId = bufferIds[0];
            textureBufferId = bufferIds[1];
            indexBufferId = bufferIds[2];
            GLES20.glGenTextures(1, textureIds, 0);
            textureId = textureIds[0];

            textureBuffer.clear();
            textureBuffer.put(geometry.getTextureCoordinates()).position(0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, textureBufferId);
            GLES20.glBufferData(
                    GLES20.GL_ARRAY_BUFFER,
                    geometry.getTextureCoordinates().length * Float.BYTES,
                    textureBuffer,
                    GLES20.GL_STATIC_DRAW);

            indexBuffer.clear();
            indexBuffer.put(geometry.getIndices()).position(0);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);
            GLES20.glBufferData(
                    GLES20.GL_ELEMENT_ARRAY_BUFFER,
                    geometry.getIndices().length * Short.BYTES,
                    indexBuffer,
                    GLES20.GL_STATIC_DRAW);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, positionBufferId);
            GLES20.glBufferData(
                    GLES20.GL_ARRAY_BUFFER,
                    geometry.getPositions().length * Float.BYTES,
                    null,
                    GLES20.GL_DYNAMIC_DRAW);
            invalidateAsset();
        }

        void setAssetPath(String assetPath) {
            this.assetPath = assetPath == null ? "" : assetPath;
        }

        void invalidateAsset() {
            uploadedAssetPath = null;
            uploadedOrientation = null;
        }

        boolean ensureAsset(PageOrientation requestedOrientation) {
            String requestedPath = assetPath;
            if (requestedPath.isEmpty()) return false;
            if (requestedPath.equals(uploadedAssetPath)
                    && requestedOrientation == uploadedOrientation) {
                return true;
            }

            Bitmap bitmap;
            try (InputStream input = context.getAssets().open(requestedPath)) {
                bitmap = BitmapFactory.decodeStream(input);
            } catch (IOException exception) {
                throw new IllegalStateException("Could not open PlayLikeCurl asset " + requestedPath, exception);
            }
            if (bitmap == null) {
                throw new IllegalStateException("Could not decode PlayLikeCurl asset " + requestedPath);
            }

            geometry = PlayLikeCurlGeometry.createPage(
                    role,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    requestedOrientation);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            bitmap.recycle();
            uploadedAssetPath = requestedPath;
            uploadedOrientation = requestedOrientation;
            return true;
        }

        void uploadPositions() {
            positionBuffer.clear();
            positionBuffer.put(geometry.getPositions()).position(0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, positionBufferId);
            GLES20.glBufferSubData(
                    GLES20.GL_ARRAY_BUFFER,
                    0,
                    geometry.getPositions().length * Float.BYTES,
                    positionBuffer);
        }
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int createdProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(createdProgram, vertexShader);
        GLES20.glAttachShader(createdProgram, fragmentShader);
        GLES20.glLinkProgram(createdProgram);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(createdProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            String log = GLES20.glGetProgramInfoLog(createdProgram);
            GLES20.glDeleteProgram(createdProgram);
            throw new IllegalStateException("Could not link PlayLikeCurl GLES2 program: " + log);
        }
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);
        return createdProgram;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] != GLES20.GL_TRUE) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("Could not compile PlayLikeCurl GLES2 shader: " + log);
        }
        return shader;
    }

    private static FloatBuffer directFloatBuffer(int size) {
        return ByteBuffer.allocateDirect(size * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
    }

    private static ShortBuffer directShortBuffer(int size) {
        return ByteBuffer.allocateDirect(size * Short.BYTES)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
    }
}
