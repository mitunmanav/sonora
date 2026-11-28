# 14. BACK acts on a cancelled key-up
Status: resolved
Type: task
Spec: B10

Act on BACK up only when the down was tracked and the up is not cancelled. One helper in `ui/Ui` used by HomeActivity, ToolActivity and ShareActivity.

## Answer

BackPress: HomeActivity, ToolActivity and ShareActivity act on a BACK up only when they saw its down and it is not cancelled. BackPressTest. Files keeps its own handling (files-core cannot see the app module).
