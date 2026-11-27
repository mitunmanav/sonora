# 11. Arranging across the pinned block snaps back
Status: resolved
Type: task
Spec: B7

Decision taken: a move between two pinned apps rewrites the pin order; a move across the edge of the pinned block is refused (the key is used, nothing moves), the same as the edge of the grid. Pure rule in `AppOrdering`, unit-tested.

## Answer

AppOrdering.canMove/pinnedCount/pinOrder. A move across the edge of the pinned block is refused; a move among the pins writes the pin order. AppOrderingTest.
