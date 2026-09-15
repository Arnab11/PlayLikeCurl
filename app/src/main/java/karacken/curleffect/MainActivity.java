package karacken.curleffect;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import karacken.curl.LandscapePageDeck;
import karacken.curl.DeckRejectionReason;
import karacken.curl.PageChange;
import karacken.curl.PageDeck;
import karacken.curl.PageDisplayRect;
import karacken.curl.PageImage;
import karacken.curl.PageLeafRole;
import karacken.curl.PageMaterial;
import karacken.curl.PageSurfaceListener;
import karacken.curl.PageSurfaceView;
import karacken.curl.PortraitPageDeck;
import karacken.curl.RenderCapabilities;
import karacken.curl.RenderFailure;

/** Standalone production-API demo using client-decoded page bitmaps. */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "PlayLikeCurlDemo";
    private static final int PAPER_FRONT_ARGB = 0xFFF5F2EA;
    private static final int PAPER_REVERSE_ARGB = 0xFFE9E3D8;
    private static final int BORDER_ARGB = 0xFF5B554C;
    private static final int BACKGROUND_ARGB = 0xFFF5F2EA;
    private static final String[] PAGE_ASSETS = {
        "portrait/page1.png",
        "portrait/page2.png",
        "portrait/page3.png",
        "portrait/page4.png",
        "portrait/page5.png",
        "portrait/page6.png",
        "portrait/page7.png",
        "portrait/page8.png"
    };

    private final ExecutorService decodeExecutor = Executors.newSingleThreadExecutor();
    private final List<Bitmap> pages = new ArrayList<>();
    private PageSurfaceView pageSurfaceView;
    private boolean landscapeSpread;
    private boolean capabilitiesAvailable;
    private boolean initialDeckSubmitted;
    private boolean hasPreparedDeck;
    private Integer pendingTargetOrdinal;
    private int currentOrdinal;
    private long nextGenerationId = 1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        landscapeSpread = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        pageSurfaceView = new PageSurfaceView(this);
        pageSurfaceView.setPageSurfaceListener(new PageSurfaceListener() {
            @Override
            public void onCapabilitiesAvailable(RenderCapabilities capabilities) {
                Log.i(
                        TAG,
                        "capabilities maxTextureSize="
                                + capabilities.getMaxTextureSize()
                                + " gpuBudgetBytes="
                                + capabilities.getGpuBudgetBytes());
                capabilitiesAvailable = true;
                submitInitialDeckIfReady();
            }

            @Override
            public void onDeckPrepared(long generationId) {
                Log.i(TAG, "deck prepared generation=" + generationId);
                hasPreparedDeck = true;
            }

            @Override
            public void onDeckRejected(
                    long generationId,
                    DeckRejectionReason reason) {
                Log.w(
                        TAG,
                        "deck rejected generation="
                                + generationId
                                + " reason="
                                + reason);
                if (reason == DeckRejectionReason.SESSION_DETACHED
                        || reason == DeckRejectionReason.CAPABILITIES_UNAVAILABLE
                        || reason == DeckRejectionReason.INVALID_CONTENT
                        || reason == DeckRejectionReason.RESOURCE_CAPACITY) {
                    pendingTargetOrdinal = null;
                    if (!hasPreparedDeck) {
                        initialDeckSubmitted = false;
                        pageSurfaceView.post(() -> submitInitialDeckIfReady());
                    }
                }
            }

            @Override
            public void onDeckReleased(
                    long generationId,
                    karacken.curl.DeckReleaseReason reason) {
                Log.i(
                        TAG,
                        "deck released generation="
                                + generationId
                                + " reason="
                                + reason);
            }

            @Override
            public void onSettlementStarted(
                    long generationId,
                    String sourceLogicalPageId,
                    String targetLogicalPageId,
                    PageChange pageChange) {
                if (pageChange == PageChange.NONE || pages.isEmpty()) {
                    return;
                }
                int target = predictTargetOrdinal(pageChange);
                pendingTargetOrdinal = target;
                PageDeck<Bitmap> pending = deckForOrdinal(target);
                Log.i(
                        TAG,
                        "submitting pending deck generation="
                                + pending.getGenerationId()
                                + " targetOrdinal="
                                + target
                                + " pageChange="
                                + pageChange);
                pageSurfaceView.submitDeck(pending);
            }

            @Override
            public void onSettlementCompleted(
                    long generationId,
                    String currentLogicalPageId,
                    int currentPageOrdinal,
                    PageChange pageChange) {
                if (pageChange == PageChange.NONE) {
                    return;
                }
                currentOrdinal = currentPageOrdinal;
                if (pendingTargetOrdinal != null
                        && pendingTargetOrdinal == currentOrdinal) {
                    Log.i(
                            TAG,
                            "pending deck covers ordinal="
                                    + currentOrdinal
                                    + "; awaiting promotion");
                    pendingTargetOrdinal = null;
                    return;
                }
                pendingTargetOrdinal = null;
                Log.i(TAG, "submitting fallback deck ordinal=" + currentOrdinal);
                pageSurfaceView.submitDeck(deckForOrdinal(currentOrdinal));
            }

            @Override
            public void onSettlementCancelled(
                    long generationId,
                    String currentLogicalPageId) {
                pendingTargetOrdinal = null;
            }

            @Override
            public void onRenderFailure(RenderFailure failure) {
                Log.e(
                        TAG,
                        "render failure generation="
                                + failure.getGenerationId()
                                + " reason="
                                + failure.getReason()
                                + " recoverable="
                                + failure.isRecoverable()
                                + " message="
                                + failure.getMessage(),
                        failure.getCause());
                Toast.makeText(
                                MainActivity.this,
                                failure.getMessage(),
                                Toast.LENGTH_SHORT)
                        .show();
            }
        });
        setContentView(pageSurfaceView);
        pageSurfaceView.addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        submitInitialDeckIfReady());
        decodePages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        pageSurfaceView.setVisible(true);
        pageSurfaceView.attach();
        submitInitialDeckIfReady();
    }

    @Override
    protected void onPause() {
        pageSurfaceView.setVisible(false);
        pageSurfaceView.detach();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        pageSurfaceView.dispose();
        decodeExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            pageSurfaceView.setVisible(true);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void decodePages() {
        decodeExecutor.execute(() -> {
            List<Bitmap> decoded = new ArrayList<>();
            try {
                for (String asset : PAGE_ASSETS) {
                    Bitmap source;
                    try (InputStream input = getAssets().open(asset)) {
                        source = BitmapFactory.decodeStream(input);
                    }
                    if (source == null) {
                        throw new IllegalStateException("Could not decode " + asset);
                    }
                    Bitmap bitmap = Bitmap.createBitmap(
                            source.getWidth(),
                            source.getHeight(),
                            Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    canvas.drawColor(Color.WHITE);
                    canvas.drawBitmap(source, 0f, 0f, null);
                    bitmap.setHasAlpha(false);
                    bitmap.setPremultiplied(true);
                    source.recycle();
                    decoded.add(bitmap);
                }
            } catch (RuntimeException | IOException exception) {
                runOnUiThread(() -> Toast.makeText(
                                MainActivity.this,
                                "Could not decode demo pages",
                                Toast.LENGTH_LONG)
                        .show());
                return;
            }
            runOnUiThread(() -> {
                pages.clear();
                pages.addAll(decoded);
                currentOrdinal = 0;
                Log.i(
                        TAG,
                        "decoded pages count="
                                + pages.size()
                                + " first="
                                + pages.get(0).getWidth()
                                + "x"
                                + pages.get(0).getHeight()
                                + " landscape="
                                + landscapeSpread);
                submitInitialDeckIfReady();
            });
        });
    }

    private void submitInitialDeckIfReady() {
        if (!capabilitiesAvailable || pages.isEmpty() || initialDeckSubmitted) {
            return;
        }
        if (pageSurfaceView.getWidth() <= 0 || pageSurfaceView.getHeight() <= 0) {
            return;
        }
        initialDeckSubmitted = true;
        PageDeck<Bitmap> deck = deckForOrdinal(currentOrdinal);
        Log.i(
                TAG,
                "submitting deck generation="
                        + deck.getGenerationId()
                        + " mode="
                        + deck.getMode()
                        + " pages="
                        + deck.getPages().size());
        pageSurfaceView.submitDeck(deck);
    }

    private int predictTargetOrdinal(PageChange pageChange) {
        int target;
        if (landscapeSpread) {
            if (pageChange == PageChange.NEXT) {
                target = even(currentOrdinal) + 2;
            } else {
                target = even(currentOrdinal) - 2;
            }
        } else {
            if (pageChange == PageChange.NEXT) {
                target = currentOrdinal + 1;
            } else {
                target = currentOrdinal - 1;
            }
        }
        return Math.max(0, Math.min(target, pages.size() - 1));
    }

    private PageDeck<Bitmap> deckForOrdinal(int ordinal) {
        long generationId = nextGenerationId++;
        int surfaceWidth = Math.max(1, pageSurfaceView.getWidth());
        int surfaceHeight = Math.max(1, pageSurfaceView.getHeight());
        if (landscapeSpread) {
            int split = Math.max(1, surfaceWidth / 2);
            PageDisplayRect leftRect = new PageDisplayRect(0, 0, split, surfaceHeight);
            PageDisplayRect rightRect =
                    new PageDisplayRect(split, 0, surfaceWidth, surfaceHeight);
            PageDisplayRect clipping =
                    new PageDisplayRect(0, 0, surfaceWidth, surfaceHeight);
            int currentLeft = Math.max(0, Math.min(even(ordinal), pages.size() - 2));
            return new LandscapePageDeck<>(
                    page(generationId, currentLeft - 2, leftRect, PageLeafRole.LEFT, clipping),
                    page(generationId, currentLeft - 1, rightRect, PageLeafRole.RIGHT, clipping),
                    page(generationId, currentLeft, leftRect, PageLeafRole.LEFT, clipping),
                    page(generationId, currentLeft + 1, rightRect, PageLeafRole.RIGHT, clipping),
                    page(generationId, currentLeft + 2, leftRect, PageLeafRole.LEFT, clipping),
                    page(generationId, currentLeft + 3, rightRect, PageLeafRole.RIGHT, clipping));
        }
        PageDisplayRect display =
                new PageDisplayRect(0, 0, surfaceWidth, surfaceHeight);
        return new PortraitPageDeck<>(
                page(generationId, ordinal - 1, display, PageLeafRole.FULL, display),
                page(generationId, ordinal, display, PageLeafRole.FULL, display),
                page(generationId, ordinal + 1, display, PageLeafRole.FULL, display));
    }

    private PageImage<Bitmap> page(
            long generationId,
            int ordinal,
            PageDisplayRect displayRect,
            PageLeafRole leafRole,
            PageDisplayRect clippingRect) {
        int boundedOrdinal = Math.max(0, Math.min(ordinal, pages.size() - 1));
        Bitmap bitmap = pages.get(boundedOrdinal);
        PageMaterial material = new PageMaterial(
                generationId,
                PAPER_FRONT_ARGB,
                PAPER_REVERSE_ARGB,
                BORDER_ARGB,
                BACKGROUND_ARGB,
                leafRole,
                displayRect,
                clippingRect,
                null,
                1);
        return new PageImage<>(
                generationId,
                PAGE_ASSETS[boundedOrdinal],
                boundedOrdinal,
                bitmap.getWidth(),
                bitmap.getHeight(),
                displayRect,
                bitmap,
                material);
    }

    private static int even(int value) {
        return value - Math.floorMod(value, 2);
    }
}
