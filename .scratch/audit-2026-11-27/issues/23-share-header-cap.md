# 23. Share: unbounded header count
Status: resolved
Type: task
Spec: C24

Stop reading after 100 header lines and answer 431.

## Answer

More than 100 header lines answer 431. FileServerTest.tooManyHeadersAnswer431.
