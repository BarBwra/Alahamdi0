package com.barbwra.mlum.client.hud.field;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The heartbeat on the wrist device, written left to right the way a bedside monitor writes it.
 *
 * <p>A ring buffer, one sample per screen column. The write head moves across the screen and a short
 * gap is kept blank ahead of it, so the newest beat is always drawn over the oldest instead of the
 * whole trace scrolling - which is both what a real monitor does and far easier to read at a glance,
 * because the waveform stays still while the head passes.</p>
 *
 * <p>The rate is the readout: 64 beats a minute at full health, rising as health falls.</p>
 */
@OnlyIn(Dist.CLIENT)
final class Ecg {

    /** Samples written per second; with a 62 column screen one sweep takes a little over two seconds. */
    private static final float RATE = 28.0F;
    /** Columns kept blank ahead of the head. */
    private static final int GAP = 6;

    private final float[] trace;
    private int head;
    private float phase;
    private float acc;
    private float bpm = 64.0F;

    Ecg(int columns) {
        trace = new float[columns];
    }

    void advance(float dt, float beatsPerMinute) {
        bpm = beatsPerMinute;
        acc += dt * RATE;
        while (acc >= 1.0F) {
            acc -= 1.0F;
            phase += bpm / 60.0F / RATE;
            if (phase >= 1.0F) {
                phase -= 1.0F;
            }
            trace[head] = sample(phase);
            head = (head + 1) % trace.length;
        }
    }

    /** 1 on the beat, falling to 0 within a sixth of it; drives the little LED beside the level. */
    float beat(long nowMs) {
        float p = (nowMs / 1000.0F * bpm / 60.0F) % 1.0F;
        return p < 0.15F ? 1.0F - p / 0.15F : 0.0F;
    }

    /** P wave, QRS spike, T wave, flat. Heights in screen pixels above the baseline. */
    private static float sample(float p) {
        if (p < 0.08F) {
            return (float) Math.sin(p / 0.08F * Math.PI) * 1.0F;
        }
        if (p < 0.14F) {
            return 0.0F;
        }
        if (p < 0.16F) {
            return -1.2F;
        }
        if (p < 0.19F) {
            return 6.0F;
        }
        if (p < 0.22F) {
            return -2.5F;
        }
        if (p < 0.34F) {
            return 0.0F;
        }
        if (p < 0.46F) {
            return (float) Math.sin((p - 0.34F) / 0.12F * Math.PI) * 1.6F;
        }
        return 0.0F;
    }

    void draw(HudPen pen, float sx, float sy, float sh, int colour) {
        int n = trace.length;
        float base = sy + Math.round(sh * 0.62F);
        int prevY = Integer.MIN_VALUE;
        int prevI = -9;
        for (int k = 0; k < n; k++) {
            int i = (head - 1 - k + n) % n;
            if (k > n - GAP) {
                prevY = Integer.MIN_VALUE;
                continue;
            }
            float px = sx + 1 + i;
            int py = Math.round(base - trace[i]);
            float a = 1.0F - k / (float) n * 0.8F;
            pen.rect(px - 1, py - 1, 3, 3, FieldHud.alpha(colour, 0.09F * a));
            if (prevY != Integer.MIN_VALUE && Math.abs(i - prevI) == 1) {
                int lo = Math.min(prevY, py);
                int hi = Math.max(prevY, py);
                pen.rect(px, lo, 1, hi - lo + 1, FieldHud.alpha(colour, a));
            } else {
                pen.rect(px, py, 1, 1, FieldHud.alpha(colour, a));
            }
            prevY = py;
            prevI = i;
        }
        int hi = (head - 1 + n) % n;
        float hx = sx + 1 + hi;
        int hy = Math.round(base - trace[hi]);
        pen.rect(hx - 2, hy - 2, 5, 5, FieldHud.alpha(colour, 0.2F));
        pen.rect(hx, hy, 1, 1, 0xF2FFFFFF);
    }
}
