package com.bimo.easytoread;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;
import android.widget.ImageView;

/** Image surface with bounded pinch/pan and page-swipe navigation at fit scale. */
@SuppressLint("AppCompatCustomView")
public final class ZoomImageView extends ImageView {
    public interface OnPageSwipeListener {
        void onNextPage();
        void onPreviousPage();
    }

    private static final float MIN_ZOOM = 1f;
    private static final float MAX_ZOOM = 5f;

    private final Matrix imageMatrix = new Matrix();
    private final float[] matrixValues = new float[9];
    private final ScaleGestureDetector scaleDetector;
    private final int touchSlop;
    private final float swipeThreshold;
    private float fitScale = 1f;
    private float zoom = 1f;
    private float lastX;
    private float lastY;
    private float downX;
    private float downY;
    private boolean scaleChanged;
    private OnPageSwipeListener pageSwipeListener;

    public ZoomImageView(Context context, AttributeSet attributes) {
        super(context, attributes);
        setScaleType(ScaleType.MATRIX);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        swipeThreshold = 64f * getResources().getDisplayMetrics().density;
        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) {
                        scaleChanged = true;
                        return true;
                    }

                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float next = clamp(zoom * detector.getScaleFactor(), MIN_ZOOM, MAX_ZOOM);
                        float factor = next / zoom;
                        zoom = next;
                        imageMatrix.postScale(factor, factor,
                                detector.getFocusX(), detector.getFocusY());
                        clampTranslation();
                        applyMatrix();
                        return true;
                    }

                    @Override
                    public void onScaleEnd(ScaleGestureDetector detector) {
                        if (zoom <= 1.01f) resetToFit();
                        else {
                            clampTranslation();
                            applyMatrix();
                        }
                    }
                });
    }

    public void setOnPageSwipeListener(OnPageSwipeListener listener) {
        pageSwipeListener = listener;
    }

    @Override
    public void setImageBitmap(Bitmap bitmap) {
        super.setImageBitmap(bitmap);
        post(this::resetToFit);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        post(this::resetToFit);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                scaleChanged = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1 && !scaleDetector.isInProgress()
                        && zoom > 1.01f) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    imageMatrix.postTranslate(dx, dy);
                    clampTranslation();
                    applyMatrix();
                    lastX = event.getX();
                    lastY = event.getY();
                }
                return true;
            case MotionEvent.ACTION_UP:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (!scaleChanged && zoom <= 1.01f && pageSwipeListener != null
                        && Math.max(Math.abs(dx), Math.abs(dy)) >= swipeThreshold) {
                    if (Math.abs(dx) >= Math.abs(dy)) {
                        if (dx < 0f) pageSwipeListener.onNextPage();
                        else pageSwipeListener.onPreviousPage();
                    } else {
                        if (dy < 0f) pageSwipeListener.onNextPage();
                        else pageSwipeListener.onPreviousPage();
                    }
                } else if (Math.abs(dx) < touchSlop && Math.abs(dy) < touchSlop) {
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    public void resetToFit() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() <= 0 || getHeight() <= 0) return;
        int sourceWidth = Math.max(1, drawable.getIntrinsicWidth());
        int sourceHeight = Math.max(1, drawable.getIntrinsicHeight());
        float widthScale = getWidth() / (float) sourceWidth;
        float heightScale = getHeight() / (float) sourceHeight;
        fitScale = Math.min(widthScale, heightScale);
        float displayedWidth = sourceWidth * fitScale;
        float displayedHeight = sourceHeight * fitScale;
        imageMatrix.reset();
        imageMatrix.postScale(fitScale, fitScale);
        imageMatrix.postTranslate(
                (getWidth() - displayedWidth) / 2f,
                (getHeight() - displayedHeight) / 2f
        );
        zoom = 1f;
        applyMatrix();
    }

    public float getRelativeZoom() {
        return zoom;
    }

    private void clampTranslation() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() <= 0 || getHeight() <= 0) return;
        imageMatrix.getValues(matrixValues);
        float matrixScale = matrixValues[Matrix.MSCALE_X];
        float renderedWidth = drawable.getIntrinsicWidth() * matrixScale;
        float renderedHeight = drawable.getIntrinsicHeight() * matrixScale;
        float currentX = matrixValues[Matrix.MTRANS_X];
        float currentY = matrixValues[Matrix.MTRANS_Y];

        float targetX;
        if (renderedWidth <= getWidth()) {
            targetX = (getWidth() - renderedWidth) / 2f;
        } else {
            targetX = clamp(currentX, getWidth() - renderedWidth, 0f);
        }

        float targetY;
        if (renderedHeight <= getHeight()) {
            targetY = (getHeight() - renderedHeight) / 2f;
        } else {
            targetY = clamp(currentY, getHeight() - renderedHeight, 0f);
        }
        imageMatrix.postTranslate(targetX - currentX, targetY - currentY);
    }

    private void applyMatrix() {
        setImageMatrix(imageMatrix);
        invalidate();
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
