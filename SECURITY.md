# Security policy

Report a suspected vulnerability privately: **Security → Advisories → Report a vulnerability** on this repository. If
that is unavailable, contact the maintainer through their [GitHub profile](https://github.com/mitunmanav).

Please do not put exploit details in a public issue.

## What is in scope

- The LAN share (`app/.../tools/FileServer.java`): it serves files over HTTP on the local network while the Share
  screen is open. Anything that reaches files outside the shared folder, keeps the server alive after the screen
  closes, or exposes it beyond the LAN is a vulnerability.
- The Files content provider (`files-core/.../FilesProvider.java`) and any exported component in the manifests.
- Anything that makes Sonora send data off the device. It makes no outbound connection by design.

Only the latest commit on `master` is supported.
