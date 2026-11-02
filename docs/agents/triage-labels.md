# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the strings used in this repo's local-markdown issue tracker, where each is written as a `Status:` line near the top of the issue file.

| Role                       | String in our tracker | Meaning                                  |
| -------------------------- | --------------------- | ---------------------------------------- |
| `needs-triage`             | `needs-triage`        | Maintainer needs to evaluate this issue  |
| `needs-info`               | `needs-info`          | Waiting on reporter for more information |
| `ready-for-agent`          | `ready-for-agent`     | Fully specified, ready for an AFK agent  |
| `ready-for-human`          | `ready-for-human`     | Requires human implementation            |
| `wontfix`                  | `wontfix`             | Will not be actioned                     |

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), write the corresponding string as the `Status:` value. Edit the middle column if the vocabulary changes.
