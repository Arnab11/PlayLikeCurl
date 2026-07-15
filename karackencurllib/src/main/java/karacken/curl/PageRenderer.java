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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** GLES2 renderer for PlayLikeCurl's original deformation and a two-leaf spread adapter. */
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

    private static final String SHADOW_VERTEX_SHADER =
            "uniform mat4 uMvpMatrix;\n"
                    + "attribute vec3 aPosition;\n"
                    + "attribute float aGradient;\n"
                    + "varying float vGradient;\n"
                    + "void main() {\n"
                    + "  gl_Position = uMvpMatrix * vec4(aPosition, 1.0);\n"
                    + "  vGradient = aGradient;\n"
                    + "}\n";

    private static final String SHADOW_FRAGMENT_SHADER =
            "precision mediump float;\n"
                    + "uniform float uOpacity;\n"
                    + "varying float vGradient;\n"
                    + "void main() {\n"
                    + "  float falloff = 1.0 - smoothstep(0.0, 1.0, vGradient);\n"
                    + "  gl_FragColor = vec4(0.0, 0.0, 0.0, uOpacity * falloff);\n"
                    + "}\n";

    private static final short[] SHADOW_INDICES = {0, 1, 2, 2, 1, 3};
    private static final float SHADOW_DEPTH = PlayLikeCurlModel.RIGHT_DEPTH + 0.00025f;

    private final Context context;
    private final GpuMesh leftMesh = new GpuMesh(PageRole.LEFT);
    private final GpuMesh frontMesh = new GpuMesh(PageRole.FRONT);
    private final GpuMesh mirroredLeftMesh = new GpuMesh(PageRole.LEFT, true);
    private final GpuMesh mirroredFrontMesh = new GpuMesh(PageRole.FRONT, true);
    private final GpuMesh rightMesh = new GpuMesh(PageRole.RIGHT);
    private final Map<String, GpuTexture> textureCache = new ConcurrentHashMap<>();
    private final PageState flatState = new PageState(
            PageRole.RIGHT, PlayLikeCurlModel.RIGHT_DEPTH, PlayLikeCurlModel.GRID, 0);
    private final PageState turningState = new PageState(
            PageRole.FRONT, PlayLikeCurlModel.FRONT_DEPTH, PlayLikeCurlModel.GRID, 0);
    private final PageState incomingState = new PageState(
            PageRole.LEFT,
            PlayLikeCurlModel.LEFT_DEPTH,
            PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION,
            0);
    private final float[] projectionMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];
    private final FloatBuffer shadowPositionBuffer = directFloatBuffer(12);
    private final FloatBuffer shadowGradientBuffer = directFloatBuffer(4);
    private final ShortBuffer shadowIndexBuffer = directShortBuffer(SHADOW_INDICES.length);

    private PlayLikeCurlModel portraitModel;
    private LandscapeSpreadModel landscapeSpreadModel;
    private String portraitLeftResource = "";
    private String portraitFrontResource = "";
    private String portraitRightResource = "";
    private String spreadPreviousLeftResource = "";
    private String spreadPreviousRightResource = "";
    private String spreadCurrentLeftResource = "";
    private String spreadCurrentRightResource = "";
    private String spreadNextLeftResource = "";
    private String spreadNextRightResource = "";
    private int viewportWidth = 1;
    private int viewportHeight = 1;
    private int program;
    private int positionAttribute;
    private int textureCoordinateAttribute;
    private int matrixUniform;
    private int textureUniform;
    private int shadowProgram;
    private int shadowPositionAttribute;
    private int shadowGradientAttribute;
    private int shadowMatrixUniform;
    private int shadowOpacityUniform;

    public PageRenderer(Context context) {
        this.context = context.getApplicationContext();
    }

    void setModel(PlayLikeCurlModel model) {
        portraitModel = model;
        landscapeSpreadModel = null;
    }

    void setLandscapeSpreadModel(LandscapeSpreadModel model) {
        landscapeSpreadModel = model;
        portraitModel = null;
    }

    public void updatePageRes(String leftResource, String frontResource, String rightResource) {
        portraitLeftResource = safePath(leftResource);
        portraitFrontResource = safePath(frontResource);
        portraitRightResource = safePath(rightResource);
        registerTexture(portraitLeftResource);
        registerTexture(portraitFrontResource);
        registerTexture(portraitRightResource);
    }

    public void updateSpreadResources(
            String previousLeftResource,
            String previousRightResource,
            String currentLeftResource,
            String currentRightResource,
            String nextLeftResource,
            String nextRightResource) {
        spreadPreviousLeftResource = safePath(previousLeftResource);
        spreadPreviousRightResource = safePath(previousRightResource);
        spreadCurrentLeftResource = safePath(currentLeftResource);
        spreadCurrentRightResource = safePath(currentRightResource);
        spreadNextLeftResource = safePath(nextLeftResource);
        spreadNextRightResource = safePath(nextRightResource);
        registerTexture(spreadPreviousLeftResource);
        registerTexture(spreadPreviousRightResource);
        registerTexture(spreadCurrentLeftResource);
        registerTexture(spreadCurrentRightResource);
        registerTexture(spreadNextLeftResource);
        registerTexture(spreadNextRightResource);
    }

    @Override
    public void onSurfaceCreated(GL10 ignored, EGLConfig config) {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        positionAttribute = GLES20.glGetAttribLocation(program, "aPosition");
        textureCoordinateAttribute = GLES20.glGetAttribLocation(program, "aTextureCoordinate");
        matrixUniform = GLES20.glGetUniformLocation(program, "uMvpMatrix");
        textureUniform = GLES20.glGetUniformLocation(program, "uTexture");
        shadowProgram = createProgram(SHADOW_VERTEX_SHADER, SHADOW_FRAGMENT_SHADER);
        shadowPositionAttribute = GLES20.glGetAttribLocation(shadowProgram, "aPosition");
        shadowGradientAttribute = GLES20.glGetAttribLocation(shadowProgram, "aGradient");
        shadowMatrixUniform = GLES20.glGetUniformLocation(shadowProgram, "uMvpMatrix");
        shadowOpacityUniform = GLES20.glGetUniformLocation(shadowProgram, "uOpacity");

        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClearDepthf(1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);

        leftMesh.initializeGl();
        frontMesh.initializeGl();
        mirroredLeftMesh.initializeGl();
        mirroredFrontMesh.initializeGl();
        rightMesh.initializeGl();
        for (GpuTexture texture : textureCache.values()) texture.resetGl();
    }

    @Override
    public void onSurfaceChanged(GL10 ignored, int width, int height) {
        viewportWidth = Math.max(width, 1);
        viewportHeight = Math.max(height, 1);
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight);
    }

    @Override
    public void onDrawFrame(GL10 ignored) {
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glUseProgram(program);
        GLES20.glUniform1i(textureUniform, 0);

        if (landscapeSpreadModel != null && viewportWidth > viewportHeight) {
            drawLandscapeSpread();
        } else if (portraitModel != null) {
            drawPortraitPage();
        }
    }

    private void drawPortraitPage() {
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight);
        updateMvp(viewportWidth, viewportHeight);
        if (portraitModel.getActivePage() == ActivePage.LEFT) {
            drawPage(
                    rightMesh,
                    portraitRightResource,
                    portraitModel.getRightPage(),
                    false,
                    PageOrientation.PORTRAIT);
            drawPage(
                    frontMesh,
                    portraitFrontResource,
                    portraitModel.getFrontPage(),
                    false,
                    PageOrientation.PORTRAIT);
            drawMovingPage(
                    leftMesh,
                    portraitLeftResource,
                    portraitModel.getLeftPage(),
                    PageOrientation.PORTRAIT);
            return;
        }
        drawPage(
                leftMesh,
                portraitLeftResource,
                portraitModel.getLeftPage(),
                false,
                PageOrientation.PORTRAIT);
        drawPage(
                rightMesh,
                portraitRightResource,
                portraitModel.getRightPage(),
                false,
                PageOrientation.PORTRAIT);
        drawMovingPage(
                frontMesh,
                portraitFrontResource,
                portraitModel.getFrontPage(),
                PageOrientation.PORTRAIT);
    }

    private void drawLandscapeSpread() {
        preloadSpreadWindow();
        int leftWidth = viewportWidth / 2;
        int rightWidth = viewportWidth - leftWidth;
        LandscapeSpreadTransition transition = landscapeSpreadModel.getTransition();

        if (transition.getProgress() == 0f) {
            drawFlatLeaf(0, leftWidth, spreadCurrentLeftResource);
            drawFlatLeaf(leftWidth, rightWidth, spreadCurrentRightResource);
            return;
        }

        if (transition.isForward()) {
            drawFlatLeaf(0, leftWidth, spreadCurrentLeftResource);
            drawFlatLeaf(leftWidth, rightWidth, spreadNextRightResource);
            turningState.setCurlPosition(transition.getTurningCurlPosition());
            incomingState.setCurlPosition(transition.getIncomingCurlPosition());
            if (transition.isTurningCurrentLeafVisible()) {
                drawLeaf(
                        leftWidth,
                        rightWidth,
                        spreadCurrentRightResource,
                        frontMesh,
                        turningState,
                        true);
            }
            if (transition.isIncomingReverseLeafVisible()) {
                drawLeaf(
                        0,
                        leftWidth,
                        spreadNextLeftResource,
                        mirroredLeftMesh,
                        incomingState,
                        true);
            }
        } else {
            drawFlatLeaf(0, leftWidth, spreadPreviousLeftResource);
            drawFlatLeaf(leftWidth, rightWidth, spreadCurrentRightResource);
            turningState.setCurlPosition(transition.getTurningCurlPosition());
            incomingState.setCurlPosition(transition.getIncomingCurlPosition());
            if (transition.isTurningCurrentLeafVisible()) {
                drawLeaf(
                        0,
                        leftWidth,
                        spreadCurrentLeftResource,
                        mirroredFrontMesh,
                        turningState,
                        true);
            }
            if (transition.isIncomingReverseLeafVisible()) {
                drawLeaf(
                        leftWidth,
                        rightWidth,
                        spreadPreviousRightResource,
                        leftMesh,
                        incomingState,
                        true);
            }
        }
    }

    private void drawFlatLeaf(int x, int width, String resource) {
        drawLeaf(x, width, resource, rightMesh, flatState, false);
    }

    private void drawLeaf(
            int x,
            int width,
            String resource,
            GpuMesh mesh,
            PageState state,
            boolean active) {
        GLES20.glViewport(x, 0, width, viewportHeight);
        updateMvp(width, viewportHeight);
        if (active) {
            drawMovingPage(mesh, resource, state, PageOrientation.PORTRAIT);
        } else {
            drawPage(mesh, resource, state, false, PageOrientation.PORTRAIT);
        }
    }

    private void drawMovingPage(
            GpuMesh mesh,
            String resource,
            PageState state,
            PageOrientation orientation) {
        drawFoldShadow(mesh, resource, state, orientation);
        drawPage(mesh, resource, state, true, orientation);
    }

    private void drawFoldShadow(
            GpuMesh mesh,
            String resource,
            PageState state,
            PageOrientation orientation) {
        GpuTexture texture = ensureTexture(resource);
        if (texture == null) return;
        FoldShadowModel.State shadow = FoldShadowModel.resolve(
                mesh.role, state.getCurlPosition(), mesh.horizontallyMirrored);
        if (shadow.getOpacity() <= 0.001f) return;

        float bitmapRatio = PlayLikeCurlGeometry.bitmapRatio(
                texture.bitmapWidth, texture.bitmapHeight, orientation);
        float heightCorrection = (bitmapRatio - 1f) / 2f;
        float bottom = -heightCorrection;
        float top = bitmapRatio - heightCorrection;
        shadowPositionBuffer.clear();
        shadowPositionBuffer.put(new float[] {
                shadow.getStartX(), bottom, SHADOW_DEPTH,
                shadow.getEndX(), bottom, SHADOW_DEPTH,
                shadow.getStartX(), top, SHADOW_DEPTH,
                shadow.getEndX(), top, SHADOW_DEPTH
        }).position(0);
        shadowGradientBuffer.clear();
        shadowGradientBuffer.put(shadow.isDarkAtStart()
                ? new float[] {0f, 1f, 0f, 1f}
                : new float[] {1f, 0f, 1f, 0f}).position(0);
        shadowIndexBuffer.clear();
        shadowIndexBuffer.put(SHADOW_INDICES).position(0);

        // The shadow uses client-side buffers. Clear the page mesh VBO bindings first;
        // otherwise GLES interprets these Java buffers as offsets into the bound VBOs.
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        GLES20.glUseProgram(shadowProgram);
        GLES20.glUniformMatrix4fv(shadowMatrixUniform, 1, false, mvpMatrix, 0);
        GLES20.glUniform1f(shadowOpacityUniform, shadow.getOpacity());
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDepthMask(false);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnableVertexAttribArray(shadowPositionAttribute);
        GLES20.glVertexAttribPointer(
                shadowPositionAttribute, 3, GLES20.GL_FLOAT, false, 0, shadowPositionBuffer);
        GLES20.glEnableVertexAttribArray(shadowGradientAttribute);
        GLES20.glVertexAttribPointer(
                shadowGradientAttribute, 1, GLES20.GL_FLOAT, false, 0, shadowGradientBuffer);
        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                SHADOW_INDICES.length,
                GLES20.GL_UNSIGNED_SHORT,
                shadowIndexBuffer);
        GLES20.glDisableVertexAttribArray(shadowPositionAttribute);
        GLES20.glDisableVertexAttribArray(shadowGradientAttribute);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(program);
        GLES20.glUniform1i(textureUniform, 0);
    }

    private void preloadSpreadWindow() {
        ensureTexture(spreadPreviousLeftResource);
        ensureTexture(spreadPreviousRightResource);
        ensureTexture(spreadCurrentLeftResource);
        ensureTexture(spreadCurrentRightResource);
        ensureTexture(spreadNextLeftResource);
        ensureTexture(spreadNextRightResource);
    }

    private void drawPage(
            GpuMesh mesh,
            String resource,
            PageState state,
            boolean active,
            PageOrientation orientation) {
        GpuTexture texture = ensureTexture(resource);
        if (texture == null) return;
        mesh.ensureGeometry(texture.bitmapWidth, texture.bitmapHeight, orientation);
        PlayLikeCurlGeometry.update(mesh.geometry, state.getCurlPosition(), active);
        mesh.applyHorizontalMirrorToPositions();
        mesh.uploadPositions();

        GLES20.glUniformMatrix4fv(matrixUniform, 1, false, mvpMatrix, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture.textureId);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.positionBufferId);
        GLES20.glEnableVertexAttribArray(positionAttribute);
        GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.textureBufferId);
        GLES20.glEnableVertexAttribArray(textureCoordinateAttribute);
        GLES20.glVertexAttribPointer(textureCoordinateAttribute, 2, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.indexBufferId);
        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                mesh.geometry.getIndices().length,
                GLES20.GL_UNSIGNED_SHORT,
                0);
        GLES20.glDisableVertexAttribArray(positionAttribute);
        GLES20.glDisableVertexAttribArray(textureCoordinateAttribute);
    }

    private void updateMvp(int width, int height) {
        Matrix.perspectiveM(
                projectionMatrix,
                0,
                45f,
                PlayLikeCurlGeometry.projectionAspect(width, height),
                0.1f,
                100f);
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, 0f, 0f, -2f);
        Matrix.translateM(modelMatrix, 0, -0.5f, -0.5f, 0f);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0);
    }

    private void registerTexture(String resource) {
        if (!resource.isEmpty()) textureCache.computeIfAbsent(resource, GpuTexture::new);
    }

    private GpuTexture ensureTexture(String resource) {
        if (resource.isEmpty()) return null;
        GpuTexture texture = textureCache.computeIfAbsent(resource, GpuTexture::new);
        texture.ensureUploaded();
        return texture;
    }

    private final class GpuTexture {
        private final String assetPath;
        private int textureId;
        private int bitmapWidth;
        private int bitmapHeight;
        private boolean uploaded;

        GpuTexture(String assetPath) {
            this.assetPath = assetPath;
        }

        void resetGl() {
            textureId = 0;
            uploaded = false;
        }

        void ensureUploaded() {
            if (uploaded) return;
            Bitmap bitmap;
            try (InputStream input = context.getAssets().open(assetPath)) {
                bitmap = BitmapFactory.decodeStream(input);
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Could not open PlayLikeCurl asset " + assetPath, exception);
            }
            if (bitmap == null) {
                throw new IllegalStateException("Could not decode PlayLikeCurl asset " + assetPath);
            }

            if (textureId == 0) {
                int[] ids = new int[1];
                GLES20.glGenTextures(1, ids, 0);
                textureId = ids[0];
            }
            bitmapWidth = bitmap.getWidth();
            bitmapHeight = bitmap.getHeight();
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            bitmap.recycle();
            uploaded = true;
        }
    }

    private final class GpuMesh {
        private final PageRole role;
        private final boolean horizontallyMirrored;
        private final FloatBuffer positionBuffer;
        private final FloatBuffer textureBuffer;
        private final ShortBuffer indexBuffer;
        private final int[] bufferIds = new int[3];
        private PageGeometry geometry;
        private int geometryWidth = -1;
        private int geometryHeight = -1;
        private PageOrientation geometryOrientation;
        private int positionBufferId;
        private int textureBufferId;
        private int indexBufferId;

        GpuMesh(PageRole role) {
            this(role, false);
        }

        GpuMesh(PageRole role, boolean horizontallyMirrored) {
            this.role = role;
            this.horizontallyMirrored = horizontallyMirrored;
            geometry = PlayLikeCurlGeometry.createPage(
                    role, 1, 1, PageOrientation.PORTRAIT);
            if (horizontallyMirrored) mirrorTextureCoordinates(geometry.getTextureCoordinates());
            positionBuffer = directFloatBuffer(geometry.getPositions().length);
            textureBuffer = directFloatBuffer(geometry.getTextureCoordinates().length);
            indexBuffer = directShortBuffer(geometry.getIndices().length);
        }

        void initializeGl() {
            GLES20.glGenBuffers(bufferIds.length, bufferIds, 0);
            positionBufferId = bufferIds[0];
            textureBufferId = bufferIds[1];
            indexBufferId = bufferIds[2];

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
            geometryWidth = -1;
            geometryHeight = -1;
            geometryOrientation = null;
        }

        void ensureGeometry(int width, int height, PageOrientation orientation) {
            if (width == geometryWidth
                    && height == geometryHeight
                    && orientation == geometryOrientation) {
                return;
            }
            geometry = PlayLikeCurlGeometry.createPage(role, width, height, orientation);
            geometryWidth = width;
            geometryHeight = height;
            geometryOrientation = orientation;
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

        void applyHorizontalMirrorToPositions() {
            if (!horizontallyMirrored) return;
            float[] positions = geometry.getPositions();
            for (int offset = 0; offset < positions.length; offset += 3) {
                positions[offset] = 1f - positions[offset];
            }
        }
    }

    private static void mirrorTextureCoordinates(float[] coordinates) {
        for (int offset = 0; offset < coordinates.length; offset += 2) {
            coordinates[offset] = 1f - coordinates[offset];
        }
    }

    private static String safePath(String path) {
        return path == null ? "" : path;
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
