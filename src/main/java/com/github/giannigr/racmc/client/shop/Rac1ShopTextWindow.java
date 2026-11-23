package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.asset.hud.Rac1HudFont;
import com.github.giannigr.racmc.asset.shop.Rac1ShopText;
import java.util.*;

/**
 * Independent original byte-font window formatter (hud_text §3.3).
 * Produces layout/scissor data only; no Minecraft font or screen policy.
 */
public final class Rac1ShopTextWindow {
    private Rac1ShopTextWindow() {}
    public record Window(int left, int top, int right, int bottom, int anchor, int startY,
                         int lineHeight, boolean centered, boolean verticallyCentered) {
        public Window {
            if (left >= right || top >= bottom || right - (long) left > 4096
                    || bottom - (long) top > 4096 || anchor < left || anchor > right
                    || lineHeight < 1 || lineHeight > 64 || Math.abs((long) startY) > 16384)
                throw new IllegalArgumentException("Invalid original text window");
        }
        int wrapWidth() { return centered ? 2 * Math.min(right - anchor, anchor - left) : right - anchor; }
    }
    public record Line(byte[] bytes, int width, int colorSlot, int x, int y, boolean visible) {
        public Line { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
    public record Layout(List<Line> lines, int maxWidth, int height, Window scissor) {
        public Layout { lines = List.copyOf(lines); }
    }
    private record Run(int start, int end, int width, int colorSlot) {}
    private record Pass(List<Run> runs, int finalColor) {}

    public static Layout layout(Rac1HudFont font, byte[] text, Window window) {
        Objects.requireNonNull(font); Objects.requireNonNull(text); Objects.requireNonNull(window);
        if (text.length > Rac1ShopText.MAX_STRING) throw new IllegalArgumentException("Oversized original text window");
        byte[] metrics = font.metrics();
        int originalWidth = window.wrapWidth();
        if (originalWidth < 1) throw new IllegalArgumentException("Empty original text wrap width");
        int width = originalWidth, color = 0;
        Pass pass = wrap(metrics, text, width, color);
        int originalLines = pass.runs.size();
        while (pass.runs.size() >= 2 && pass.runs.getLast().width < width / 3) {
            int nextWidth = width - 16;
            // Safety diagnostic for pathological/narrow user windows, never an invented negative-width layout.
            if (nextWidth < 1) break;
            color = pass.finalColor;
            Pass next = wrap(metrics, text, nextWidth, color);
            if (next.runs.size() > originalLines) {
                pass = wrap(metrics, text, originalWidth, next.finalColor);
                break;
            }
            width = nextWidth; pass = next;
        }
        int height = pass.runs.size() * window.lineHeight;
        int y = window.startY - (window.verticallyCentered ? height / 2 : 0);
        int maxWidth = 0;
        List<Line> lines = new ArrayList<>();
        for (Run run : pass.runs) {
            int x = window.anchor - (window.centered ? run.width >> 1 : 0);
            boolean visible = y + window.lineHeight >= window.top && y <= window.bottom;
            lines.add(new Line(Arrays.copyOfRange(text, run.start, run.end), run.width, run.colorSlot, x, y, visible));
            maxWidth = Math.max(maxWidth, run.width);
            y += window.lineHeight;
        }
        return new Layout(lines, maxWidth, height, window);
    }

    private static Pass wrap(byte[] metrics, byte[] text, int width, int initialColor) {
        List<Run> lines = new ArrayList<>();
        int start = 0, color = initialColor;
        while (start < text.length && text[start] != 0) {
            int at = start, measured = 0, lastBreak = -1, widthAtBreak = 0;
            while (at < text.length && text[at] != 0 && text[at] != 1) {
                int c = text[at] & 255;
                int advance = advance(metrics, c);
                if (measured + advance > width && at > start) break;
                if (c == ' ' || c < 0x10) { lastBreak = at; widthAtBreak = measured; }
                measured += advance; at++;
                if (measured > width) break; // one oversize glyph still makes forward progress
            }
            int end = at, next = at;
            if (at < text.length && text[at] == 1) next++;
            else if (at < text.length && text[at] != 0 && lastBreak > start && widthAtBreak > 0) {
                end = lastBreak; measured = widthAtBreak; next = lastBreak + 1;
            }
            lines.add(new Run(start, end, measured, color));
            for (int i = start; i < next; i++) {
                int c = text[i] & 255;
                if (c >= 8 && c <= 15) color = c - 8;
            }
            if (next <= start) throw new IllegalArgumentException("Original text window made no progress");
            start = next;
        }
        return new Pass(List.copyOf(lines), color);
    }
    private static int advance(byte[] metrics, int c) {
        if (c * 4 + 3 >= metrics.length) throw new IllegalArgumentException("Unsupported original window byte " + c);
        return metrics[c * 4 + 3];
    }
}
