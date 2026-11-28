package com.mitun.sonora.ui;

/** Three clamped 5×5 box-blur passes for the wallpaper thumbnail. */
final class BackdropBlur {
    private BackdropBlur() {}

    static int[] soften(int[] input, int width, int height) {
        int[] output = new int[input.length];
        long[] columns = new long[width];
        for (int pass = 0; pass < 3; pass++) {
            int secondRow = Math.min(1, height - 1) * width;
            int thirdRow = Math.min(2, height - 1) * width;
            for (int x = 0; x < width; x++) {
                columns[x] = 3 * channels(input[x]) + channels(input[secondRow + x])
                        + channels(input[thirdRow + x]);
            }
            for (int y = 0; y < height; y++) {
                long sum = 3 * columns[0] + columns[Math.min(1, width - 1)]
                        + columns[Math.min(2, width - 1)];
                int row = y * width;
                for (int x = 0; x < width; x++) {
                    output[row + x] = average(sum);
                    sum += columns[Math.min(x + 3, width - 1)] - columns[Math.max(x - 2, 0)];
                }
                int entering = Math.min(y + 3, height - 1) * width;
                int leaving = Math.max(y - 2, 0) * width;
                for (int x = 0; x < width; x++) {
                    columns[x] += channels(input[entering + x]) - channels(input[leaving + x]);
                }
            }
            int[] swap = input;
            input = output;
            output = swap;
        }
        return input;
    }

    /** Each 16-bit field can hold 25 samples at full intensity without carrying. */
    private static long channels(int pixel) {
        return ((long) (pixel & 0xff0000) << 16) | ((long) (pixel & 0xff00) << 8) | (pixel & 0xff);
    }

    private static int average(long sum) {
        int red = (int) (sum >>> 32) / 25;
        int green = (int) ((sum >>> 16) & 0xffff) / 25;
        int blue = (int) (sum & 0xffff) / 25;
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }
}
