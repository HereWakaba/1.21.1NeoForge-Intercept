package com.ryjs.intercept.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;


public final class TaiChiName {

    private static final String BASE = "End Of TaiChi";
    private static final String ALT = "太极终焉";

    private static final String KATA =
            "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲンヴ";

    private static final long SWEEP_MS = 2200L;
    private static final long WINDOW_MS = 1500L;
    private static final long ALT_HOLD_MS = 100L;
    private static final double BAND = 2.0;

    private TaiChiName() {}

    private static long hash(long x) {
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= x >>> 33;
        return x & 0x7fffffffffffffffL;
    }


    public static boolean isAlt(long ms) {
        long cycle = ms / WINDOW_MS;
        long r = hash(cycle * 2654435761L) % 100L;
        if (r < 30L) {
            return (ms % WINDOW_MS) < ALT_HOLD_MS;
        }
        return false;
    }


    public static String currentText(long ms) {
        if (isAlt(ms)) {
            return ALT;
        }
        return scramble(ms);
    }

    private static String scramble(long ms) {
        int len = BASE.length();
        double phase = (ms % (2L * SWEEP_MS)) / (double) SWEEP_MS;

        double front = phase < 1.0 ? phase * len : (2.0 - phase) * len;

        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = BASE.charAt(i);
            if (c == ' ') {
                sb.append(' ');
                continue;
            }
            if (Math.abs(i - front) < BAND) {

                int idx = (int) (hash(i * 131L + ms / 40L) % KATA.length());
                sb.append(KATA.charAt(idx));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }


    public static int charColor(int index, int total, long ms) {
        double phase = ms * 0.005 - index * 0.6;
        double v = 0.5 + 0.5 * Math.sin(phase);
        int g = (int) (v * 255.0);
        return 0xFF000000 | (g << 16) | (g << 8) | g;
    }


    public static MutableComponent asComponent(long ms) {
        String s = currentText(ms);
        MutableComponent out = Component.empty();
        int n = s.length();
        for (int i = 0; i < n; i++) {
            int color = charColor(i, n, ms) & 0xFFFFFF;
            out.append(Component.literal(String.valueOf(s.charAt(i)))
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
        }
        return out;
    }
}
