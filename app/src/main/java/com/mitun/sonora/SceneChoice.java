package com.mitun.sonora;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Which aerials the screen saver may show, and which a category lists.
 *
 * Pure logic, so it can be tested without a TV.
 */
public final class SceneChoice {
    private SceneChoice() {
    }

    /**
     * The next scene to show, skipping the hidden ones.
     *
     * Hiding every scene cannot mean a black screen, so then all of them count
     * as shown. The Choose Aerials screen says so.
     *
     * @param current the scene on screen, or -1 when there is none yet
     * @param step    1 for the next scene, -1 for the one before
     * @return the index to show, or -1 when there are no scenes at all
     */
    public static int next(int current, int step, boolean[] hidden) {
        int count = hidden.length;
        if (count == 0) {
            return -1;
        }
        int direction = step < 0 ? -1 : 1;
        // With nothing on screen yet, going backwards lands on the last scene.
        int index = current < 0 && direction < 0 ? 0 : current;
        boolean ignoreHidden = allHidden(hidden);
        for (int i = 0; i < count; i++) {
            index = (index + direction + count) % count;
            if (ignoreHidden || !hidden[index]) {
                return index;
            }
        }
        return -1;
    }

    /** True when there is at least one scene and none of them is left to show. */
    public static boolean allHidden(boolean[] hidden) {
        for (boolean value : hidden) {
            if (!value) {
                return false;
            }
        }
        return hidden.length > 0;
    }

    /**
     * The scenes a category lists.
     *
     * @param regions the region of every scene
     * @param wanted  the region to list, or null for all of them
     */
    public static List<Integer> inRegion(String[] regions, String wanted) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < regions.length; i++) {
            if (wanted == null || wanted.equals(regions[i])) {
                out.add(i);
            }
        }
        return out;
    }

    /** Scene count including device photos. */
    public static int count(Context context) {
        return Scenes.all(context).size();
    }
}
