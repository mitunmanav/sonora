# 01. One branch: master
Status: ready-for-human
Type: task
Spec: A1, A2

Close PRs #6, #8, #9. Delete every remote branch except `master`, this working branch last, after it lands.

Accept: `git ls-remote --heads origin` lists `master` only; no PR open from a bot.

## Answer

PRs #6, #8 and #9 are closed. The branch deletes were refused: this session may only push its own branch. Left for the owner: delete the ten branches on GitHub, then this one once it is on master.
