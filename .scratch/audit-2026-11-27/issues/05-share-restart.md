# 05. Share screen dead after a quick leave-and-return
Status: resolved
Type: task
Spec: B1 (from PR #9)

Fresh `FileServer` per start; `stop()` closes without joining the accept thread; `ShareActivity` stops synchronously.

Accept: unit test start → stop → start on fresh servers leaves a listening port; tests green.

## Answer

Fresh FileServer per start; stop() closes without a join; ShareActivity stops on the main thread. FileServerTest.aQuickRestartStillAnswers.
