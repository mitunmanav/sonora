# 13. DOWN from a column with nothing under it
Status: resolved
Type: task
Spec: B9

Decision taken: match tvOS. DOWN goes to the last tile of the next row when the next row is shorter. Same rule while arranging. `GridTest` changes with it.

## Answer

Grid.moveTarget and arrange mode: DOWN onto a shorter row lands on its last tile. GridTest updated.
