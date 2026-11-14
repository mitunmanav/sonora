# Browser pass

Status: needs-triage

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

- Which WebView version does the target TV ship, and does the current UI
  assume anything newer than minSdk 23?
