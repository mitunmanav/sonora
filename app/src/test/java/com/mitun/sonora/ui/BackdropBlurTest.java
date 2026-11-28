package com.mitun.sonora.ui;

import static org.junit.Assert.assertArrayEquals;

import java.util.Random;

import org.junit.Test;

public class BackdropBlurTest {
    @Test
    public void matchesTheOriginalAcrossClampedEdgesAndAllThreePasses() {
        Random random = new Random(38);
        for (int[] size : new int[][]{{1, 1}, {1, 7}, {8, 1}, {2, 3}, {3, 2}, {7, 9}, {96, 54}}) {
            int width = size[0];
            int height = size[1];
            int[] pixels = new int[width * height];
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = random.nextInt();
            }
            assertArrayEquals(width + "×" + height, reference(pixels.clone(), width, height),
                    BackdropBlur.soften(pixels, width, height));
        }
    }

    static int[] reference(int[] input, int width, int height) {
        int[] output = new int[width * height];
        for (int pass = 0; pass < 3; pass++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int r = 0;
                    int g = 0;
                    int b = 0;
                    for (int dy = -2; dy <= 2; dy++) {
                        int row = Math.max(0, Math.min(height - 1, y + dy)) * width;
                        for (int dx = -2; dx <= 2; dx++) {
                            int pixel = input[row + Math.max(0, Math.min(width - 1, x + dx))];
                            r += (pixel >> 16) & 0xff;
                            g += (pixel >> 8) & 0xff;
                            b += pixel & 0xff;
                        }
                    }
                    output[y * width + x] = 0xff000000 | (r / 25) << 16 | (g / 25) << 8 | b / 25;
                }
            }
            int[] swap = input;
            input = output;
            output = swap;
        }
        return input;
    }
}
