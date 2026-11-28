# 12. Keys that do not wake the screen saver
Status: resolved
Type: task
Spec: B8

One pure key table for the in-app saver and the dream: LEFT/RIGHT change scene, OK shows the clock once in "On tap" mode and wakes after, every other key wakes. Volume and mute keys pass through untouched.

Accept: unit test on the table; both callers use it.

## Answer

SaverKeys, one table used by the home-screen saver and SonoraDreamService. Any key but the arrows, OK and the volume wakes; the search key wakes the in-app saver too. SaverKeysTest.
