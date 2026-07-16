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
import karacken.curl.PageImage;
import karacken.curl.PageSurfaceListener;
import karacken.curl.PageSurfaceView;
import karacken.curl.PortraitPageDeck;
import karacken.curl.RenderCapabilities;
import karacken.curl.RenderFailure;

/** Standalone production-API demo using client-decoded page bitmaps. */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "PlayLikeCurlDemo";
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
                if (reason == DeckRejectionReason.SESSION_DETACHED) {
                    initialDeckSubmitted = false;
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
            public void onSettlementCompleted(
                    long generationId,
                    String currentLogicalPageId,
                    int currentPageOrdinal,
                    PageChange pageChange) {
                if (pageChange == PageChange.NONE) {
                    return;
                }
                currentOrdinal = currentPageOrdinal;
                pageSurfaceView.submitDeck(deckForCurrentOrdinal());
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
        initialDeckSubmitted = true;
        PageDeck<Bitmap> deck = deckForCurrentOrdinal();
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

    private PageDeck<Bitmap> deckForCurrentOrdinal() {
        long generationId = nextGenerationId++;
        if (landscapeSpread) {
            int currentLeft = Math.max(0, Math.min(even(currentOrdinal), pages.size() - 2));
            return new LandscapePageDeck<>(
                    page(generationId, currentLeft - 2),
                    page(generationId, currentLeft - 1),
                    page(generationId, currentLeft),
                    page(generationId, currentLeft + 1),
                    page(generationId, currentLeft + 2),
                    page(generationId, currentLeft + 3));
        }
        return new PortraitPageDeck<>(
                page(generationId, currentOrdinal - 1),
                page(generationId, currentOrdinal),
                page(generationId, currentOrdinal + 1));
    }

    private PageImage<Bitmap> page(long generationId, int ordinal) {
        int boundedOrdinal = Math.max(0, Math.min(ordinal, pages.size() - 1));
        Bitmap bitmap = pages.get(boundedOrdinal);
        return new PageImage<>(
                generationId,
                PAGE_ASSETS[boundedOrdinal],
                boundedOrdinal,
                bitmap.getWidth(),
                bitmap.getHeight(),
                bitmap);
    }

    private static int even(int value) {
        return value - Math.floorMod(value, 2);
    }
}
