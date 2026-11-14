package com.mitun.sonora.tools;

import android.content.Context;
import android.content.pm.PackageManager;

import com.mitun.sonora.PackageActions;

import java.util.ArrayList;
import java.util.List;
import com.mitun.sonora.Platform;

/**
 * Puts the TV back the way it was after a system update.
 *
 * An update from the manufacturer brings back the apps that were turned off:
 * the trackers, the sponsored content rows, and the TV's own app launcher,
 * which is where the adverts and the lag come from. This turns them off
 * again - and only the ones that are actually on, so nothing is touched
 * twice and nothing is reported that did not happen.
 */
public final class Cleanup {
    /**
     * The apps that are turned off.
     *
     * They are all trackers, setup leftovers, sponsored content or the TV's
     * own launcher. Nothing a person uses to watch something is in here.
     */
    private static final String[] PACKAGES = {
            // Trackers and setup leftovers.
            "com.miui.tv.analytics",
            "com.xiaomi.statistic",
            "com.xiaomo.tv.milegal",
            "com.google.android.feedback",
            "com.google.android.partnersetup",
            "com.google.android.onetimeinitializer",
            "com.google.android.tungsten.setupwraith",
            "android.autoinstalls.config.xioami.mibox3",
            "com.google.android.play.games",
            "com.android.printspooler",
            "com.google.android.syncadapters.calendar",
            // The TV's own content and recommendation rows.
            "com.mitv.tvhome.atv",
            "com.mitv.tvhome.mitvplus",
            "com.mitv.tvhome.michannel",
            "com.xm.webcontent",
            "com.xiaomi.android.tvsetup.partnercustomizer",
            "com.google.android.tvrecommendations",
            "com.mitv.dream",
            "com.google.android.backdrop",
            // The TV's own app launcher: the one with the sponsored rows.
            "com.google.android.tvlauncher",
    };

    /** getApplicationEnabledSetting has no "not installed" answer of its own. */
    private static final int NOT_INSTALLED = -1;

    private Cleanup() {
    }

    /** What the run did, so the screen can say what actually happened. */
    public static final class Result {
        public final int turnedOff;
        public final int alreadyOff;
        public final int failed;
        public final List<String> problems;

        Result(int turnedOff, int alreadyOff, int failed, List<String> problems) {
            this.turnedOff = turnedOff;
            this.alreadyOff = alreadyOff;
            this.failed = failed;
            this.problems = problems;
        }

        public String message() {
            if (failed > 0) {
                // Name them: a bare count leaves the user guessing which
                // apps the system refused to turn off.
                StringBuilder names = new StringBuilder();
                for (String pkg : problems) {
                    if (names.length() > 0) {
                        names.append(", ");
                    }
                    names.append(pkg);
                }
                return turnedOff + " turned off, " + failed + " could not be changed: "
                        + names;
            }
            if (turnedOff == 0) {
                return "Nothing to do. Everything was already off";
            }
            return turnedOff + (turnedOff == 1 ? " app turned off" : " apps turned off");
        }
    }

    /**
     * Turns off everything in the list that is currently on.
     *
     * Safe to run as often as you like: an app that is already off is left
     * alone.
     */
    public static Result run(Context context) {
        PackageManager pm = context.getPackageManager();
        int turnedOff = 0;
        int alreadyOff = 0;
        int failed = 0;
        List<String> problems = new ArrayList<>();
        boolean sonoraIsHome = Platform.isDefaultLauncher(context);
        for (String pkg : PACKAGES) {
            if (!sonoraIsHome && "com.google.android.tvlauncher".equals(pkg)) {
                // Turning off the stock launcher while it still answers HOME
                // leaves the button with nowhere to go.
                continue;
            }
            int state = state(pm, pkg);
            if (state == NOT_INSTALLED
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) {
                alreadyOff++;
                continue;
            }
            PackageActions.Result result = PackageActions.setPackageEnabled(context, pkg, false);
            if (result.ok) {
                turnedOff++;
            } else {
                failed++;
                problems.add(pkg);
            }
        }
        return new Result(turnedOff, alreadyOff, failed, problems);
    }

    /**
     * How a package is set up right now.
     *
     * DEFAULT is not an answer of its own - it means whatever the manifest
     * says - so the resolved enabled flag is read instead. A package that is
     * not installed is NOT_INSTALLED, and one the system cannot answer for is
     * reported as on: it gets tried, and counted when the try fails.
     */
    private static int state(PackageManager pm, String packageName) {
        try {
            int setting = pm.getApplicationEnabledSetting(packageName);
            if (setting != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                return setting;
            }
            return pm.getApplicationInfo(packageName, 0).enabled
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        } catch (PackageManager.NameNotFoundException | IllegalArgumentException e) {
            return NOT_INSTALLED;
        } catch (RuntimeException e) {
            return PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        }
    }
}
