# 18. Cold app load does three package calls per app
Status: resolved
Type: task
Spec: D1

`AppRepository.collect` trusts the launcher query it just made and drops the two launch-intent lookups per package.

## Answer

The two launch-intent lookups per package are gone from AppRepository.collect.
