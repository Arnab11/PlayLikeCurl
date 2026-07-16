package karacken.curl.consumer;

import android.content.Context;
import android.graphics.Bitmap;
import karacken.curl.DeckRejectionReason;
import karacken.curl.DeckReleaseReason;
import karacken.curl.PageImage;
import karacken.curl.PageSurfaceListener;
import karacken.curl.PageSurfaceView;
import karacken.curl.PlayLikeCurlApi;
import karacken.curl.PortraitPageDeck;
import karacken.curl.RenderCapabilities;

/** Compile-only proof that the production API is usable outside the library package. */
public final class ExternalConsumer {
    private ExternalConsumer() {}

    public static int apiVersion() {
        return PlayLikeCurlApi.PRODUCTION_API_VERSION;
    }

    public static PageSurfaceView create(
            Context context,
            long generationId,
            Bitmap previous,
            Bitmap current,
            Bitmap next,
            PageSurfaceListener listener) {
        PageSurfaceView view = new PageSurfaceView(context);
        PortraitPageDeck<Bitmap> deck = new PortraitPageDeck<>(
                page(generationId, "previous", 0, previous),
                page(generationId, "current", 1, current),
                page(generationId, "next", 2, next));
        view.setPageSurfaceListener(new PageSurfaceListener() {
            @Override
            public void onCapabilitiesAvailable(RenderCapabilities capabilities) {
                view.submitDeck(deck);
                listener.onCapabilitiesAvailable(capabilities);
            }

            @Override
            public void onDeckRejected(
                    long rejectedGenerationId,
                    DeckRejectionReason reason) {
                listener.onDeckRejected(rejectedGenerationId, reason);
            }

            @Override
            public void onDeckReleased(
                    long releasedGenerationId,
                    DeckReleaseReason reason) {
                listener.onDeckReleased(releasedGenerationId, reason);
            }
        });
        return view;
    }

    private static PageImage<Bitmap> page(
            long generationId,
            String logicalPageId,
            int ordinal,
            Bitmap bitmap) {
        return new PageImage<>(
                generationId,
                logicalPageId,
                ordinal,
                bitmap.getWidth(),
                bitmap.getHeight(),
                bitmap);
    }
}
