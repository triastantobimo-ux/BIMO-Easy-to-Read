package com.bimo.easytoread.core;

import java.util.Objects;

public final class Box {
    private final int left;
    private final int top;
    private final int right;
    private final int bottom;

    public Box(int left, int top, int right, int bottom) {
        this.left = Math.min(left, right);
        this.top = Math.min(top, bottom);
        this.right = Math.max(left, right);
        this.bottom = Math.max(top, bottom);
    }

    public int getLeft() { return left; }
    public int getTop() { return top; }
    public int getRight() { return right; }
    public int getBottom() { return bottom; }
    public int width() { return right - left; }
    public int height() { return bottom - top; }
    public int centerX() { return left + width() / 2; }

    public double horizontalOverlapRatio(Box other) {
        int overlap = Math.max(0, Math.min(right, other.right) - Math.max(left, other.left));
        int minimumWidth = Math.max(1, Math.min(width(), other.width()));
        return (double) overlap / minimumWidth;
    }

    public Box union(Box other) {
        return new Box(
                Math.min(left, other.left),
                Math.min(top, other.top),
                Math.max(right, other.right),
                Math.max(bottom, other.bottom)
        );
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof Box)) return false;
        Box box = (Box) value;
        return left == box.left && top == box.top && right == box.right && bottom == box.bottom;
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, top, right, bottom);
    }
}
