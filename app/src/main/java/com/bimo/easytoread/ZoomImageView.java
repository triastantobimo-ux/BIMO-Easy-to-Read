package com.bimo.easytoread;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

public final class ZoomImageView extends ImageView {
    private final Matrix imageMatrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private float fitScale = 1f;
    private float zoom = 1f;
    private float lastX;
    private float lastY;

    public ZoomImageView(Context context, AttributeSet attributes) {
        super(context, attributes);
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float next = Math.max(1f, Math.min(5f, zoom * detector.getScaleFactor()));
                float factor = next / zoom;
                zoom = next;
                imageMatrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                setImageMatrix(imageMatrix);
                return true;
            }
        });
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
        if (event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lastX = event.getX();
                lastY = event.getY();
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && zoom > 1f) {
                float dx = event.getX() - lastX;
                float dy = event.getY() - lastY;
                imageMatrix.postTranslate(dx, dy);
                setImageMatrix(imageMatrix);
                lastX = event.getX();
                lastY = event.getY();
            }
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    public void resetToFit() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) return;
        float widthScale = getWidth() / (float) Math.max(1, drawable.getIntrinsicWidth());
        float heightScale = getHeight() / (float) Math.max(1, drawable.getIntrinsicHeight());
        fitScale = Math.min(widthScale, heightScale);
        float displayedWidth = drawable.getIntrinsicWidth() * fitScale;
        float displayedHeight = drawable.getIntrinsicHeight() * fitScale;
        imageMatrix.reset();
        imageMatrix.postScale(fitScale, fitScale);
        imageMatrix.postTranslate(
                (getWidth() - displayedWidth) / 2f,
                (getHeight() - displayedHeight) / 2f
        );
        zoom = 1f;
        setImageMatrix(imageMatrix);
    }
}
