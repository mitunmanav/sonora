# 08. Apps changed outside Sonora never reach the grid
Status: resolved
Type: task
Spec: B4

On resume ask what changed: `PackageManager.getChangedPackages(seq)` on API 26+, a `LauncherApps.Callback` held between onStart and onStop below that. Rebuild only on a change. The decision (sequence moved? callback fired?) is pure and unit-tested.

Accept: unit test; on TV, adb install while another app is open, HOME, tile is there. Needs TV.

## Answer

AppChanges: getChangedPackages on API 26+, a LauncherApps.Callback held from onCreate to onDestroy below that. onResume drops the app and app-manager caches only when something moved. AppChangesTest. Needs TV: adb install while another app is open, then HOME.
