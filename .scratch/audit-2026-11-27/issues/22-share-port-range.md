# 22. Share: a new port every visit, no seeking
Status: resolved
Type: task
Spec: C23, C25, D5

Try a fixed port first (8080, then 8081-8089), fall back to any. Answer a single `Range: bytes=a-b` with 206 and `Accept-Ranges: bytes`; anything else gets the whole file. Range parsing is pure and unit-tested.

## Answer

Ports 8080-8089 first, with SO_REUSEADDR, then any port. A single bytes range answers 206 with Content-Range; past the end answers 416; anything else is a plain 200 with Accept-Ranges. FileServerTest.
