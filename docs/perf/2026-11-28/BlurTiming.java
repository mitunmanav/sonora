package com.mitun.sonora.ui;

import java.util.Arrays;
import java.util.Random;

final class BlurTiming {
    private static volatile int sink;

    private static double time(int[] pixels, boolean rolling, int iterations) {
        long elapsed = 0;
        for (int i = 0; i < iterations; i++) {
            int[] input = pixels.clone();
            long started = System.nanoTime();
            int[] blurred = rolling ? BackdropBlur.soften(input, 96, 54)
                    : BackdropBlurTest.reference(input, 96, 54);
            elapsed += System.nanoTime() - started;
            sink = blurred[i % blurred.length];
        }
        return elapsed / (1000.0 * iterations);
    }

    public static void main(String[] args) {
        int[] pixels = new Random(38).ints(96 * 54).toArray();
        if (!Arrays.equals(BackdropBlur.soften(pixels.clone(), 96, 54),
                           BackdropBlurTest.reference(pixels.clone(), 96, 54))) {
            throw new AssertionError("blur output changed");
        }
        time(pixels, false, 1200);
        time(pixels, true, 1200);
        System.out.println("batch,old_us,rolling_us");
        for (int i = 0; i < 8; i++) {
            boolean rollingFirst = i % 2 != 0;
            double first = time(pixels, rollingFirst, 350);
            double second = time(pixels, !rollingFirst, 350);
            System.out.printf("%d,%.3f,%.3f%n", i + 1,
                              rollingFirst ? second : first, rollingFirst ? first : second);
        }
    }
}
