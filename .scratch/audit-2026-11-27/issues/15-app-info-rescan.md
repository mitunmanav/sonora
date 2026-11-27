# 15. App info always throws the app list away
Status: wontfix
Type: task
Blocked by: 08
Spec: B11

Drop the blind invalidate on the App info hand-off; ticket 08 finds what really changed.

## Answer

Kept as it is. The package manager's change count is documented for
installs, updates and removals; a disable on the App info screen may not
move it, and then the ghost tile that the invalidate fixed would be back.
One rescan after the user visited App info is the cheaper mistake.
