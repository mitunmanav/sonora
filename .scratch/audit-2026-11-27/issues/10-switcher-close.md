# 10. Switcher long-OK claims to close apps
Status: resolved
Type: task
Spec: B6

Decision taken: long OK removes the card from the switcher for this session and says "Removed from the list". It no longer calls `killBackgroundProcesses`, which cannot close the app the user just left. The removed packages are pure state in `RecentApps`, unit-tested.

Accept: a removed app stays out of the switcher until Sonora opens it again.

## Answer

Long OK calls RecentApps.dismiss and says "Removed from the list"; the app comes back once it is used again. No killBackgroundProcesses. RecentAppsTest. README and ARCHITECTURE say what the gesture does.
