package karacken.curl;

final class PlayLikeCurlGeometry {
    static final float CAMERA_DISTANCE = 2f;
    static final float FIELD_OF_VIEW_DEGREES = 45f;

    private PlayLikeCurlGeometry() {
    }

    static PageGeometry createPage(
            PageRole role,
            int displayWidth,
            int displayHeight,
            PageOrientation orientation) {
        if (displayWidth <= 0 || displayHeight <= 0) {
            throw new IllegalArgumentException("Display dimensions must be positive");
        }
        float pageRatio = pageRatio(displayWidth, displayHeight, orientation);
        int vertexCount = (PlayLikeCurlModel.GRID + 1) * (PlayLikeCurlModel.GRID + 1);
        PageGeometry page = new PageGeometry(
                role,
                pageRatio,
                new float[vertexCount * 3],
                createTextureCoordinates(),
                createIndices());
        update(
                page,
                role == PageRole.LEFT
                        ? PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION
                        : PlayLikeCurlModel.GRID,
                false);
        return page;
    }

    static void update(PageGeometry page, float curlPosition, boolean active) {
        int grid = PlayLikeCurlModel.GRID;
        float heightCorrection = (page.getBitmapRatio() - 1f) / 2f;
        float R = PlayLikeCurlModel.RADIUS;
        float xc = curlPosition / (float) grid;
        
        float alpha = (float) Math.toRadians(-15);
        float cosA = (float) Math.cos(alpha);
        float sinA = (float) Math.sin(alpha);
        
        for (int row = 0; row <= grid; row++) {
            for (int column = 0; column <= grid; column++) {
                int offset = 3 * (row * (grid + 1) + column);
                float x = column / (float) grid;
                float y = row / (float) grid * page.getBitmapRatio() - heightCorrection;
                
                if (page.getRole() == PageRole.RIGHT) {
                    page.getPositions()[offset] = x;
                    page.getPositions()[offset + 1] = y;
                    page.getPositions()[offset + 2] = depth(page.getRole());
                    continue;
                }
                
                float d = (x - xc) * cosA + y * sinA;
                
                if (d <= 0f) {
                    page.getPositions()[offset] = x;
                    page.getPositions()[offset + 1] = y;
                    page.getPositions()[offset + 2] = active ? 
                        (page.getRole() == PageRole.LEFT ? PlayLikeCurlModel.LEFT_DEPTH : PlayLikeCurlModel.FRONT_DEPTH) 
                        : depth(page.getRole());
                } else {
                    float theta = d / R;
                    float dNew;
                    float zNew;
                    if (theta <= Math.PI) {
                        dNew = (float) (R * Math.sin(theta));
                        zNew = (float) (R - R * Math.cos(theta));
                    } else {
                        dNew = (float) -(d - Math.PI * R);
                        zNew = 2f * R;
                    }
                    
                    float dx = (dNew - d) * cosA;
                    float dy = (dNew - d) * sinA;
                    
                    page.getPositions()[offset] = x + dx;
                    page.getPositions()[offset + 1] = y + dy;
                    
                    float baseDepth = active ? 
                        (page.getRole() == PageRole.LEFT ? PlayLikeCurlModel.LEFT_DEPTH : PlayLikeCurlModel.FRONT_DEPTH) 
                        : depth(page.getRole());
                    page.getPositions()[offset + 2] = baseDepth + zNew;
                }
            }
        }
    }

    static float projectionAspect(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Viewport dimensions must be positive");
        }
        return width / (float) height;
    }

    static float pageRatio(int width, int height, PageOrientation orientation) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Display dimensions must be positive");
        }
        return orientation == PageOrientation.PORTRAIT
                ? height / (float) width
                : width / (float) height;
    }

    static float visiblePlaneHeight() {
        return visiblePlaneHeight(PlayLikeCurlModel.RIGHT_DEPTH);
    }

    static float visiblePlaneHeight(float planeDepth) {
        if (!Float.isFinite(planeDepth) || planeDepth >= CAMERA_DISTANCE) {
            throw new IllegalArgumentException("Plane depth must remain in front of the camera");
        }
        float restingDistance = CAMERA_DISTANCE - planeDepth;
        return (float) (2f
                * restingDistance
                * Math.tan(Math.toRadians(FIELD_OF_VIEW_DEGREES / 2f)));
    }

    static float restingPlaneScale(
            int width,
            int height,
            PageOrientation orientation) {
        return restingPlaneScale(
                width, height, orientation, PlayLikeCurlModel.RIGHT_DEPTH);
    }

    static float restingPlaneScale(
            int width,
            int height,
            PageOrientation orientation,
            float planeDepth) {
        return visiblePlaneHeight(planeDepth) / pageRatio(width, height, orientation);
    }

    static float foldEdgeX(PageRole role, float curlPosition) {
        if (role == PageRole.FRONT) {
            return frontX(PlayLikeCurlModel.GRID, curlPosition);
        }
        if (role == PageRole.LEFT) {
            return leftX(PlayLikeCurlModel.GRID, curlPosition);
        }
        return 1f;
    }

    static float foldEdgeDepth(PageRole role, float curlPosition) {
        return activeDepth(role, PlayLikeCurlModel.GRID, curlPosition);
    }

    static float projectXOntoDepthPlane(
            float sourceX,
            float sourceDepth,
            float targetDepth) {
        return 0.5f
                + (sourceX - 0.5f)
                * (CAMERA_DISTANCE - targetDepth)
                / (CAMERA_DISTANCE - sourceDepth);
    }

    private static float frontX(int column, float curlPosition) {
        return curlPosition / (float) PlayLikeCurlModel.GRID;
    }

    private static float leftX(int column, float curlPosition) {
        return curlPosition / (float) PlayLikeCurlModel.GRID;
    }

    private static float activeDepth(PageRole role, int column, float curlPosition) {
        return role == PageRole.LEFT ? PlayLikeCurlModel.LEFT_DEPTH : PlayLikeCurlModel.FRONT_DEPTH;
    }

    private static float resolvedRadius(float percentage) {
        return percentage < 0.20f
                ? PlayLikeCurlModel.RADIUS * percentage * 5f
                : PlayLikeCurlModel.RADIUS;
    }

    private static float depth(PageRole role) {
        if (role == PageRole.LEFT) return PlayLikeCurlModel.LEFT_DEPTH;
        if (role == PageRole.FRONT) return PlayLikeCurlModel.FRONT_DEPTH;
        return PlayLikeCurlModel.RIGHT_DEPTH;
    }

    private static float[] createTextureCoordinates() {
        int grid = PlayLikeCurlModel.GRID;
        float[] coordinates = new float[(grid + 1) * (grid + 1) * 2];
        for (int row = 0; row <= grid; row++) {
            for (int column = 0; column <= grid; column++) {
                int offset = 2 * (row * (grid + 1) + column);
                coordinates[offset] = column / (float) grid;
                coordinates[offset + 1] = 1f - row / (float) grid;
            }
        }
        return coordinates;
    }

    private static short[] createIndices() {
        int grid = PlayLikeCurlModel.GRID;
        short[] indices = new short[grid * grid * 6];
        for (int row = 0; row < grid; row++) {
            for (int column = 0; column < grid; column++) {
                int offset = 6 * (row * grid + column);
                indices[offset] = (short) (row * (grid + 1) + column);
                indices[offset + 1] = (short) (row * (grid + 1) + column + 1);
                indices[offset + 2] = (short) ((row + 1) * (grid + 1) + column);
                indices[offset + 3] = (short) (row * (grid + 1) + column + 1);
                indices[offset + 4] = (short) ((row + 1) * (grid + 1) + column + 1);
                indices[offset + 5] = (short) ((row + 1) * (grid + 1) + column);
            }
        }
        return indices;
    }
}
