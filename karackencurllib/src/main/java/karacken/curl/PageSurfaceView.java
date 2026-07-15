package karacken.curl;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

/** Modern GLES2 surface preserving PlayLikeCurl's original gesture and settlement model. */
public class PageSurfaceView extends GLSurfaceView {
    private static final int SWIPE_MIN_DISTANCE = 120;
    private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;

    private final PageRenderer renderer;
    private final GestureDetector gestureDetector;
    private PageCurlAdapter pageCurlAdapter;
    private PlayLikeCurlModel model;
    private ValueAnimator settlementAnimator;
    private boolean settlementRunning;
    private OnPageChangeListener onPageChangeListener;

    public PageSurfaceView(Context context) {
        super(context);
        renderer = new PageRenderer(context);
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent event) {
                return true;
            }

            @Override
            public boolean onFling(
                    MotionEvent downEvent,
                    MotionEvent moveEvent,
                    float velocityX,
                    float velocityY) {
                if (model == null || downEvent == null) return false;
                if (Math.abs(downEvent.getY() - moveEvent.getY()) > SWIPE_MAX_OFF_PATH) return false;
                if (Math.abs(velocityX) < SWIPE_THRESHOLD_VELOCITY) return false;
                if (downEvent.getX() - moveEvent.getX() > SWIPE_MIN_DISTANCE) {
                    settle(model.flingTowardNext());
                    return true;
                }
                if (moveEvent.getX() - downEvent.getX() > SWIPE_MIN_DISTANCE) {
                    settle(model.flingTowardPrevious());
                    return true;
                }
                return false;
            }
        });

        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setPreserveEGLContextOnPause(true);
        setClickable(true);
        setFocusable(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (model == null) return true;
        if (settlementRunning) return true;
        boolean detectorHandled = gestureDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                model.beginGesture(event.getX());
                break;
            case MotionEvent.ACTION_MOVE:
                model.dragTo(event.getX(), getWidth());
                break;
            case MotionEvent.ACTION_UP:
                if (!detectorHandled) settle(model.release());
                performClick();
                break;
            case MotionEvent.ACTION_CANCEL:
                model.cancelGesture();
                break;
            default:
                break;
        }
        return true;
    }

    public boolean onPageTouchEvent(MotionEvent event) {
        return onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    public PageCurlAdapter getPageCurlAdapter() {
        return pageCurlAdapter;
    }

    public void setPageCurlAdapter(PageCurlAdapter pageCurlAdapter) {
        if (pageCurlAdapter == null || pageCurlAdapter.getCount() == 0) {
            throw new IllegalArgumentException("PlayLikeCurl requires at least one page");
        }
        this.pageCurlAdapter = pageCurlAdapter;
        model = new PlayLikeCurlModel(pageCurlAdapter.getCount(), 0);
        renderer.setModel(model);
        processPage();
    }

    public void setCurrentPosition(int position) {
        requireModel();
        model.jumpTo(position);
        processPage();
    }

    public int getCurrentPosition() {
        requireModel();
        return model.getCurrentPosition();
    }

    public OnPageChangeListener getOnPageChangeListener() {
        return onPageChangeListener;
    }

    public void setOnPageChangeListener(OnPageChangeListener listener) {
        onPageChangeListener = listener;
    }

    private void settle(Settlement settlement) {
        float startPercent = currentPagePercent();
        if (startPercent == settlement.getTargetPercent()) {
            completeSettlement(settlement);
            return;
        }
        if (settlementAnimator != null) settlementAnimator.cancel();
        settlementRunning = true;
        settlementAnimator = ValueAnimator.ofFloat(startPercent, settlement.getTargetPercent());
        settlementAnimator.setDuration(settlement.getDurationMillis());
        settlementAnimator.setInterpolator(toAndroidInterpolator(settlement.getInterpolator()));
        settlementAnimator.addUpdateListener(animation ->
                model.updateSettlement((float) animation.getAnimatedValue()));
        settlementAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) completeSettlement(settlement);
                settlementRunning = false;
            }
        });
        settlementAnimator.start();
    }

    private void completeSettlement(Settlement settlement) {
        int previousPosition = model.getCurrentPosition();
        model.completeSettlement(settlement);
        if (model.getCurrentPosition() != previousPosition) {
            processPage();
            if (onPageChangeListener != null) {
                onPageChangeListener.onPageChanged(model.getCurrentPosition());
            }
        }
    }

    private float currentPagePercent() {
        PageState activeState;
        if (model.getActivePage() == ActivePage.LEFT) {
            activeState = model.getLeftPage();
        } else if (model.getActivePage() == ActivePage.RIGHT) {
            activeState = model.getRightPage();
        } else {
            activeState = model.getFrontPage();
        }
        return activeState.getCurlPosition() / PlayLikeCurlModel.GRID * 100f;
    }

    private void processPage() {
        int position = model.getCurrentPosition();
        int last = pageCurlAdapter.getCount() - 1;
        renderer.updatePageRes(
                pageCurlAdapter.getItemResource(Math.max(0, position - 1)),
                pageCurlAdapter.getItemResource(position),
                pageCurlAdapter.getItemResource(Math.min(last, position + 1)));
    }

    private void requireModel() {
        if (model == null) throw new IllegalStateException("Set a PageCurlAdapter first");
    }

    private static Interpolator toAndroidInterpolator(SettlementInterpolator interpolator) {
        return interpolator == SettlementInterpolator.DECELERATE
                ? new DecelerateInterpolator()
                : new AccelerateDecelerateInterpolator();
    }

    public interface OnPageChangeListener {
        void onPageChanged(int position);
    }
}
