# 19. A pin rebuilds every tile
Status: resolved
Type: task
Spec: D2

`applyOrder` reorders the existing tiles and calls `layoutRows` instead of building new views.

## Answer

applyOrder reorders the existing tiles (reorderTiles) and only builds the grid when a tile is missing.
