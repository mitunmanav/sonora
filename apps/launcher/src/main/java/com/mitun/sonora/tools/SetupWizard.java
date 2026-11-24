package com.mitun.sonora.tools;

import com.mitun.sonora.Prefs;
import com.mitun.sonora.ui.SettingRow;
import com.mitun.sonora.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * The first-run setup.
 *
 * It runs once, it never blocks the launcher, and BACK walks back through it.
 * The mode picked on the first screen decides how much it asks: Default asks
 * the few questions that matter to anybody, Technical asks all of them.
 */
public final class SetupWizard {
    private static final int WELCOME = 0;
    private static final int WALLPAPER = 1;
    private static final int CLOCK = 2;
    private static final int SCREENSAVER = 3;
    private static final int APPS = 4;
    private static final int ROLE = 5;
    private static final int DONE = 6;

    private final ToolActivity host;

    public SetupWizard(ToolActivity host) {
        this.host = host;
    }

    /** The first screen of the setup. */
    public void start() {
        host.stack().pushSettings(titleFor(0), rowsFor(0));
    }

    /** Rebuilds the screen the user is on, e.g. after the wallpaper picker. */
    public void refreshCurrent() {
        int here = position();
        host.stack().refreshSettings(titleFor(here), rowsFor(here));
    }

    private void next() {
        int next = position() + 1;
        if (next >= sequence().length) {
            finish();
            return;
        }
        host.stack().pushSettings(titleFor(next), rowsFor(next));
    }

    /**
     * Where the setup is.
     *
     * It follows the stack, so BACK walks back through the setup and the
     * steps after it stay in step with the screens.
     */
    private int position() {
        return Math.max(0, host.stack().depth() - 1);
    }

    private void finish() {
        host.prefs().setWelcomeSeen(true);
        host.finish();
    }

    /** The steps, in order. Technical setup asks one more. */
    private int[] sequence() {
        if (host.prefs().technical()) {
            return new int[]{WELCOME, WALLPAPER, CLOCK, SCREENSAVER, APPS, ROLE, DONE};
        }
        return new int[]{WELCOME, WALLPAPER, CLOCK, SCREENSAVER, ROLE, DONE};
    }

    private int stepFor(int position) {
        int[] steps = sequence();
        return steps[Math.max(0, Math.min(steps.length - 1, position))];
    }

    private String titleFor(int position) {
        switch (stepFor(position)) {
            case WALLPAPER:
                return "Wallpaper";
            case CLOCK:
                return "Clock";
            case SCREENSAVER:
                return "Screen saver";
            case APPS:
                return "Apps on screen";
            case ROLE:
                return "Home screen";
            case DONE:
                return "All set";
            default:
                return "Set up Sonora";
        }
    }

    private String subtitleFor(int position) {
        if (stepFor(position) == DONE) {
            return "That is everything";
        }
        // Where the user is, and how much is left. A screen that says neither
        // is a screen the user has to guess their way through.
        return "Step " + (position + 1) + " of " + sequence().length;
    }

    private List<SettingRow> rowsFor(int position) {
        Prefs prefs = host.prefs();
        List<SettingRow> rows = new ArrayList<>();
        // The split layout has no subtitle; the step counter is a note.
        rows.add(SettingRow.note(subtitleFor(position)));
        switch (stepFor(position)) {
            case WALLPAPER:
                rows.add(SettingRow.note("Pick the picture that sits behind your apps."));
                rows.add(SettingRow.choice("Wallpaper", Pickers.wallpaperName(prefs),
                        () -> Pickers.wallpaper(host, this::refreshCurrent)));
                rows.add(SettingRow.note("Five photographs, free to use with credit. "
                        + "You can also use a photo from this TV."));
                break;
            case CLOCK:
                rows.add(SettingRow.note("Choose how the time is shown in the corner."));
                if (prefs.technical()) {
                    rows.add(SettingRow.toggle("24-hour clock", prefs.clock24(), () -> {
                        prefs.setClock24(!prefs.clock24());
                        refreshCurrent();
                    }));
                    rows.add(SettingRow.toggle("Show the date", prefs.showDate(), () -> {
                        prefs.setShowDate(!prefs.showDate());
                        refreshCurrent();
                    }));
                    rows.add(SettingRow.toggle("Show seconds", prefs.showSeconds(), () -> {
                        prefs.setShowSeconds(!prefs.showSeconds());
                        refreshCurrent();
                    }));
                } else {
                    rows.add(SettingRow.choice("Clock", Pickers.clockName(prefs),
                            () -> Pickers.clock(host, this::refreshCurrent)));
                    rows.add(SettingRow.toggle("Show the date", prefs.showDate(), () -> {
                        prefs.setShowDate(!prefs.showDate());
                        refreshCurrent();
                    }));
                }
                break;
            case SCREENSAVER:
                rows.add(SettingRow.note("The TV drifts through the pictures when you leave it alone."));
                rows.add(SettingRow.choice("Start after", Pickers.delayName(prefs),
                        () -> Pickers.screensaverDelay(host, this::refreshCurrent)));
                if (prefs.technical()) {
                    rows.add(SettingRow.choice("Each scene", Pickers.sceneName(prefs),
                            () -> Pickers.sceneDuration(host, this::refreshCurrent)));
                    rows.add(SettingRow.choice("Clock", Pickers.screensaverClockName(prefs),
                            () -> Pickers.screensaverClock(host, this::refreshCurrent)));
                }
                rows.add(SettingRow.note("It stops the moment you press a button."));
                if (Ui.dreamState(host) != Ui.DreamState.SELECTED) {
                    rows.add(SettingRow.action("Use Sonora when the TV dozes",
                            () -> Ui.openDreamSettings(host)));
                }
                break;
            case APPS:
                rows.add(SettingRow.note("How the apps are laid out on the home screen."));
                rows.add(SettingRow.choice("Apps per row", Pickers.columnsName(host),
                        () -> Pickers.columns(host, this::refreshCurrent)));
                rows.add(SettingRow.choice("App names", Pickers.labelName(prefs),
                        () -> Pickers.labels(host, this::refreshCurrent)));
                break;
            case ROLE:
                rows.add(SettingRow.value("Home screen",
                        Ui.isDefaultLauncher(host) ? "Sonora" : "Not yet"));
                if (!Ui.isDefaultLauncher(host)) {
                    rows.add(SettingRow.action("Set Sonora as the home screen",
                            () -> Ui.openDefaultLauncherSettings(host)));
                }
                rows.add(SettingRow.note("Sonora works either way. Without this, BACK leaves "
                        + "for the TV's own home screen."));
                break;
            case DONE:
                rows.add(SettingRow.note("Use the D-pad to move, OK to open an app, "
                        + "hold OK for its options."));
                break;
            default:
                rows.add(SettingRow.note("Sonora is a quiet home screen for your TV. "
                        + "It shows your apps, plays a screen saver and gets out of the way."));
                rows.add(SettingRow.choice("Mode", Pickers.modeName(prefs),
                        () -> Pickers.mode(host, this::refreshCurrent)));
                rows.add(SettingRow.note("Default shows the few settings that matter. "
                        + "Technical shows every setting, including the ones you will never touch."));
                rows.add(SettingRow.action("Skip setup", this::finish));
                break;
        }
        if (stepFor(position) != DONE) {
            rows.add(SettingRow.action(stepFor(position) == WELCOME ? "Start setup" : "Next",
                    this::next));
        } else {
            rows.add(SettingRow.action("Start using Sonora", this::finish));
        }
        return rows;
    }
}
