# 17. Files: list header on empty folders; Quick Look has no entrance
Status: resolved
Type: task
Spec: A table (PR #7)

Redo PR #7 as an owner change: an empty, unreadable or blocked folder in list view hides the Name/Date/Size/Kind header; Quick Look fades in like the sheets, skipped on LOW.

## Answer

From PR #7: the list header hides on empty, unreadable and blocked folders; Quick Look fades in over 120 ms, not on LOW.
