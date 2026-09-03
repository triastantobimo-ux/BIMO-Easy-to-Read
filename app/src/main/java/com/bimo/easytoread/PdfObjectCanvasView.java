package com.bimo.easytoread;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/** Maps a tap on a fit-center preview back into PDF page-point coordinates. */
public final class PdfObjectCanvasView extends AppCompatImageView {
    public interface OnPdfPointTapListener {
        void onPdfPointTap(PointF point);
    }

    private float pageWidth;
    private float pageHeight;
    private OnPdfPointTapListener listener;

    public PdfObjectCanvasView(@NonNull Context context) {
        super(context);
        initialize();
    }

    public PdfObjectCanvasView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public PdfObjectCanvasView(@NonNull Context context, @Nullable AttributeSet attrs,
                               int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        setScaleType(ScaleType.FIT_CENTER);
        setClickable(true);
        setFocusable(true);
    }

    public void setPdfPageSize(float width, float height) {
        pageWidth = Math.max(1f, width);
        pageHeight = Math.max(1f, height);
    }

    public void setOnPdfPointTapListener(OnPdfPointTapListener value) {
        listener = value;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_UP) {
            return super.onTouchEvent(event);
        }
        performClick();
        if (listener == null || getDrawable() == null || pageWidth <= 0f || pageHeight <= 0f) {
            return true;
        }
        Matrix inverse = new Matrix();
        if (!getImageMatrix().invert(inverse)) return true;
        float[] point = {event.getX(), event.getY()};
        inverse.mapPoints(point);
        float drawableWidth = Math.max(1f, getDrawable().getIntrinsicWidth());
        float drawableHeight = Math.max(1f, getDrawable().getIntrinsicHeight());
        if (point[0] < 0f || point[1] < 0f
                || point[0] > drawableWidth || point[1] > drawableHeight) return true;
        listener.onPdfPointTap(new PointF(
                point[0] / drawableWidth * pageWidth,
                point[1] / drawableHeight * pageHeight
        ));
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}

