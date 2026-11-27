# 09. Force stop never stops anything
Status: resolved
Type: task
Spec: B5

Decision taken (owner said proceed on the spec's lean): drop the `getRunningAppProcesses` pre-check, which since API 22 only sees Sonora; call `killBackgroundProcesses` and say what Android does with it: "Asked Android to stop it. An app that is on screen or playing keeps running."

Accept: no message claims a state Sonora cannot read.

## Answer

The getRunningAppProcesses pre-check is gone (it only ever saw Sonora). Force stop calls killBackgroundProcesses and says "Asked Android to stop it. An app on screen or playing keeps running."
