package com.bimo.easytoread;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import java.util.Arrays;

public final class NotchedTabDrawable extends Drawable {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path fillPath = new Path();
    private final Path accentPath = new Path();
    private final int normalColor;
    private final int pressedColor;
    private final boolean selected;
    private final float radius;
    private final float notchDepth;
    private final float notchHalfWidth;
    private boolean pressed;

    public NotchedTabDrawable(Context context, boolean selected) {
        this.selected = selected;
        this.normalColor = context.getColor(R.color.accent_primary);
        this.pressedColor = context.getColor(R.color.accent_primary_pressed);
        this.radius = dp(context, 25);
        this.notchDepth = dp(context, 9);
        this.notchHalfWidth = dp(context, 28);

        fillPaint.setStyle(Paint.Style.FILL);
        accentPaint.setStyle(Paint.Style.STROKE);
        accentPaint.setStrokeWidth(dp(context, 3));
        accentPaint.setStrokeCap(Paint.Cap.ROUND);
        accentPaint.setStrokeJoin(Paint.Join.ROUND);
        accentPaint.setColor(context.getColor(R.color.accent_secondary));
    }

    @Override
    public void draw(Canvas canvas) {
        if (!selected || getBounds().isEmpty()) return;

        float left = getBounds().left;
        float top = getBounds().top;
        float right = getBounds().right;
        float bottom = getBounds().bottom - accentPaint.getStrokeWidth() / 2f;
        float center = (left + right) / 2f;
        float usableRadius = Math.min(radius, (bottom - top) / 2f);

        fillPaint.setColor(pressed ? pressedColor : normalColor);
        fillPath.reset();
        fillPath.moveTo(left + usableRadius, top);
        fillPath.lineTo(right - usableRadius, top);
        fillPath.quadTo(right, top, right, top + usableRadius);
        fillPath.lineTo(right, bottom - usableRadius);
        fillPath.quadTo(right, bottom, right - usableRadius, bottom);
        fillPath.lineTo(center + notchHalfWidth, bottom);
        fillPath.cubicTo(
                center + notchHalfWidth * 0.58f,
                bottom,
                center + notchHalfWidth * 0.54f,
                bottom - notchDepth,
                center,
                bottom - notchDepth
        );
        fillPath.cubicTo(
                center - notchHalfWidth * 0.54f,
                bottom - notchDepth,
                center - notchHalfWidth * 0.58f,
                bottom,
                center - notchHalfWidth,
                bottom
        );
        fillPath.lineTo(left + usableRadius, bottom);
        fillPath.quadTo(left, bottom, left, bottom - usableRadius);
        fillPath.lineTo(left, top + usableRadius);
        fillPath.quadTo(left, top, left + usableRadius, top);
        fillPath.close();
        canvas.drawPath(fillPath, fillPaint);

        accentPath.reset();
        accentPath.moveTo(left + usableRadius, bottom);
        accentPath.lineTo(center - notchHalfWidth, bottom);
        accentPath.cubicTo(
                center - notchHalfWidth * 0.58f,
                bottom,
                center - notchHalfWidth * 0.54f,
                bottom - notchDepth,
                center,
                bottom - notchDepth
        );
        accentPath.cubicTo(
                center + notchHalfWidth * 0.54f,
                bottom - notchDepth,
                center + notchHalfWidth * 0.58f,
                bottom,
                center + notchHalfWidth,
                bottom
        );
        accentPath.lineTo(right - usableRadius, bottom);
        canvas.drawPath(accentPath, accentPaint);
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean nextPressed = Arrays.stream(state)
                .anyMatch(value -> value == android.R.attr.state_pressed);
        if (pressed == nextPressed) return false;
        pressed = nextPressed;
        invalidateSelf();
        return true;
    }

    @Override
    public boolean isStateful() {
        return selected;
    }

    @Override
    public void setAlpha(int alpha) {
        fillPaint.setAlpha(alpha);
        accentPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        accentPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    private static float dp(Context context, float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }
}
