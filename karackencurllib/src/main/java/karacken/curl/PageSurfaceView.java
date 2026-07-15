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
    private LandscapeSpreadModel landscapeSpreadModel;
    private boolean landscapeSpreadEnabled;
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
                PlayLikeCurlModel interaction = interactionModelOrNull();
                if (interaction == null || downEvent == null) return false;
                if (Math.abs(downEvent.getY() - moveEvent.getY()) > SWIPE_MAX_OFF_PATH) return false;
                if (Math.abs(velocityX) < SWIPE_THRESHOLD_VELOCITY) return false;
                if (downEvent.getX() - moveEvent.getX() > SWIPE_MIN_DISTANCE) {
                    settle(interaction.flingTowardNext());
                    return true;
                }
                if (moveEvent.getX() - downEvent.getX() > SWIPE_MIN_DISTANCE) {
                    settle(interaction.flingTowardPrevious());
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
        PlayLikeCurlModel interaction = interactionModelOrNull();
        if (interaction == null) return true;
        if (settlementRunning) return true;
        boolean detectorHandled = gestureDetector.onTouchEvent(event);
        float gestureWidth = landscapeSpreadModel != null
                ? Math.max(1f, getWidth() / 2f)
                : Math.max(1f, getWidth());
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                interaction.beginGesture(event.getX());
                break;
            case MotionEvent.ACTION_MOVE:
                if (landscapeSpreadModel != null) {
                    landscapeSpreadModel.dragTo(event.getX(), gestureWidth);
                } else {
                    interaction.dragTo(event.getX(), gestureWidth);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (!detectorHandled) settle(interaction.release());
                performClick();
                break;
            case MotionEvent.ACTION_CANCEL:
                interaction.cancelGesture();
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

    public void setLandscapeSpreadEnabled(boolean enabled) {
        if (landscapeSpreadEnabled == enabled) return;
        landscapeSpreadEnabled = enabled;
        if (pageCurlAdapter != null) installModel(0);
    }

    public void setPageCurlAdapter(PageCurlAdapter pageCurlAdapter) {
        if (pageCurlAdapter == null || pageCurlAdapter.getCount() == 0) {
            throw new IllegalArgumentException("PlayLikeCurl requires at least one page");
        }
        this.pageCurlAdapter = pageCurlAdapter;
        installModel(0);
    }

    public void setCurrentPosition(int position) {
        requireModel();
        if (landscapeSpreadModel != null) {
            landscapeSpreadModel.jumpTo(position);
        } else {
            model.jumpTo(position);
        }
        processPage();
    }

    public int getCurrentPosition() {
        requireModel();
        return landscapeSpreadModel != null
                ? landscapeSpreadModel.getCurrentPageIndex()
                : model.getCurrentPosition();
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
        settlementAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            if (landscapeSpreadModel != null) {
                landscapeSpreadModel.updateSettlement(value);
            } else {
                model.updateSettlement(value);
            }
        });
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
        int previousPosition = getCurrentPosition();
        if (landscapeSpreadModel != null) {
            landscapeSpreadModel.completeSettlement(settlement);
        } else {
            model.completeSettlement(settlement);
        }
        if (getCurrentPosition() != previousPosition) {
            processPage();
            if (onPageChangeListener != null) {
                onPageChangeListener.onPageChanged(getCurrentPosition());
            }
        }
    }

    private float currentPagePercent() {
        PlayLikeCurlModel interactionModel = interactionModel();
        PageState activeState;
        if (interactionModel.getActivePage() == ActivePage.LEFT) {
            activeState = interactionModel.getLeftPage();
        } else if (interactionModel.getActivePage() == ActivePage.RIGHT) {
            activeState = interactionModel.getRightPage();
        } else {
            activeState = interactionModel.getFrontPage();
        }
        return activeState.getCurlPosition() / PlayLikeCurlModel.GRID * 100f;
    }

    private void processPage() {
        int last = pageCurlAdapter.getCount() - 1;
        if (landscapeSpreadModel != null) {
            renderer.updateSpreadResources(
                    pageCurlAdapter.getItemResource(landscapeSpreadModel.getPreviousLeftPageIndex()),
                    pageCurlAdapter.getItemResource(landscapeSpreadModel.getPreviousRightPageIndex()),
                    pageCurlAdapter.getItemResource(landscapeSpreadModel.getCurrentLeftPageIndex()),
                    pageCurlAdapter.getItemResource(landscapeSpreadModel.getCurrentRightPageIndex()),
                    pageCurlAdapter.getItemResource(landscapeSpreadModel.getNextLeftPageIndex()),
                    pageCurlAdapter.getItemResource(landscapeSpreadModel.getNextRightPageIndex()));
            return;
        }
        int position = model.getCurrentPosition();
        renderer.updatePageRes(
                pageCurlAdapter.getItemResource(Math.max(0, position - 1)),
                pageCurlAdapter.getItemResource(position),
                pageCurlAdapter.getItemResource(Math.min(last, position + 1)));
    }

    private void requireModel() {
        if (model == null && landscapeSpreadModel == null) {
            throw new IllegalStateException("Set a PageCurlAdapter first");
        }
    }

    private void installModel(int position) {
        if (landscapeSpreadEnabled) {
            landscapeSpreadModel = new LandscapeSpreadModel(pageCurlAdapter.getCount(), position);
            model = null;
            renderer.setLandscapeSpreadModel(landscapeSpreadModel);
        } else {
            model = new PlayLikeCurlModel(pageCurlAdapter.getCount(), position);
            landscapeSpreadModel = null;
            renderer.setModel(model);
        }
        processPage();
    }

    private PlayLikeCurlModel interactionModel() {
        return landscapeSpreadModel != null ? landscapeSpreadModel.getMotionModel() : model;
    }

    private PlayLikeCurlModel interactionModelOrNull() {
        return landscapeSpreadModel != null ? landscapeSpreadModel.getMotionModel() : model;
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
