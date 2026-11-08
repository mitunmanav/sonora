# Browser pass

Status: wontfix — the browser was cut 2026-11-08; the product ships the
launcher and the file manager only (PLAN.md session 34). Kept as the
record of the passes done.

Two passes done 2026-11-06 (PLAN.md sessions 30–31): D-pad trap fix,
BACK/focus rework, blocked-page history fix, localOrHttps/normalise
hardening (incl. hex/octet-bypass and >255-octet literals), AdBlock parser
fixes, bookmark Open/Delete, honest clear-data label, renderer-kill
handling, launch focus and immersive re-apply. What is left for the next
agent is the on-device check below with `scripts/tv.sh`: the UP/DOWN
escape inside page text fields (`canScrollVertically` varies across
WebView builds and cannot see in-page focus), BACK sequencing from the
address bar, the blocked page rendering, the renderer-kill toast, and
the singleTask relaunch after "Turn off".

A dedicated pass over `browser/` (`BrowserActivity`, `AdBlock`,
`FileServer`-adjacent transfer bits) — the optional bundled browser — for
tvOS-26 look, edge cases and D-pad behaviour. Separate effort; the browser
stays off until the user turns it on (ToolsPanel rule: no network unless
switched on).

## Scope

- Every screen/state: URL entry, page view, history/bookmarks if any,
  error/empty states, ad-block feedback.
- Focus paths and BACK behaviour, including leaving the browser cleanly.
- Honest capability: say what the device cannot do; no fake features.
- Edge cases: no network, captive portal, permission loss, oversized pages
  on a 1 GB target (`DeviceTier` LOW answers required for any new effect).
- On-device check with `scripts/tv.sh`.

## Out of scope

- New browser features, new dependencies (zero-dependency rule stands).

## Open questions

- Which WebView version does the target TV ship? Answered in part: the
  code assumes nothing newer than API 21 (`WebResourceRequest`,
  `onShowFileChooser`, `removeAllCookies(callback)`), so minSdk 23 is safe
  on any WebView build old enough to matter. The device check remains.
