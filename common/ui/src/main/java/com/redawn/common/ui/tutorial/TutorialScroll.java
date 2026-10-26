package com.redawn.common.ui.tutorial;

/** Shared wheel/drag math for both independently scrolling panels. */
public final class TutorialScroll {
    private double offset, maximum;
    private int viewport, content;
    public double offset() { return offset; }
    public double maximum() { return maximum; }
    public void layout(int viewport, int content) {
        this.viewport = Math.max(1, viewport); this.content = Math.max(1, content);
        maximum = Math.max(0, content - viewport);
        set(offset);
    }
    public void set(double value) { offset = Math.max(0, Math.min(maximum, value)); }
    public void wheel(double delta) { set(offset - delta * 28); }
    public int thumb() { return Math.min(viewport, Math.max(18, (int) ((long) viewport * viewport / content))); }
    public int top() { return maximum == 0 ? 0 : (int) ((viewport - thumb()) * offset / maximum); }
    public void drag(double mouse, double grab) {
        set(viewport <= thumb() ? 0 : (mouse - grab) * maximum / (viewport - thumb()));
    }
}
