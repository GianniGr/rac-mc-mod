package com.github.giannigr.racmc.client;

/** Original two-stage counters, with closing moved inside the owner's 4-second deadline.
 * Nominal regional update cadence, not a claim of emulator-measured wall-clock timing. */
public final class Rac1BoltHudAnimation {
    private long pickup, next;
    private int panel, text;
    public record Phase(float panel, float text) {}
    public void reset() { pickup = next = 0; panel = text = 0; }
    public Phase update(long now, long lastPickup, int panelMax, int textMax, long step) {
        if (lastPickup == 0 || lastPickup > now) { reset(); return new Phase(0, 0); }
        if (pickup == 0 || lastPickup - pickup >= 4_000_000_000L) {
            reset(); pickup = lastPickup; next = pickup + step;
        } else if (lastPickup > pickup) {
            advance(lastPickup, panelMax, textMax, step);
            pickup = lastPickup; // reverse a partial close, do not collapse/restart an open panel
        }
        advance(now, panelMax, textMax, step);
        if (!Rac1BoltHudTime.visible(lastPickup, now)) return new Phase(0, 0);
        return new Phase(panel / (float) panelMax, text / (float) textMax);
    }
    private void advance(long now, int panelMax, int textMax, long step) {
        if (now < next) return;
        long close = pickup + 4_000_000_000L - (panelMax + textMax) * step;
        // At most four seconds of meaningful counter work, even after a long hidden/menu gap.
        long end = Math.min(now, pickup + 4_000_000_000L);
        while (next <= end) {
            if (next <= close) {
                if (panel < panelMax) panel++;
                else if (text < textMax) text++;
            } else if (text > 0) text--;
            else if (panel > 0) panel--;
            next += step;
        }
    }
    public static int tween(int from, int to, float phase) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int a = (from >>> shift) & 255, b = (to >>> shift) & 255;
            result |= (int) (a + (b - a) * phase) << shift;
        }
        return result;
    }
}
