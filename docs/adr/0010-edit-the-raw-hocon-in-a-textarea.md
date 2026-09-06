# ADR-0010: Edit the raw HOCON in a textarea, saved with an optimistic check

- **Status:** Accepted
- **Date:** 2026-09-06
- **Supersedes:** —
- **Amends:** —

## Context

M2 is the other half of the original idea: change which connections exist from the page,
not only read them. modelrack4j's `store()` already owns the hard part — it validates the
whole configuration and publishes it *before* writing the file, so text that would not load
is refused rather than saved and discovered at the next start. What was open is the shape of
the editing surface, and the owner settled it: a textarea over the raw HOCON, with the file
staying exactly where it is (an argument or `RACKCHAT_CONFIG`).

## Forces

- **A form over the schema's fields** is friendlier and can refuse nonsense earlier, but it
  has to be kept in step with modelrack4j's schema by hand, and it cannot express what HOCON
  can — comments, substitutions like `${OPENAI_API_KEY}`, includes, layering. An editor that
  silently drops a comment or resolves a substitution would be worse than no editor.
- **A textarea over the raw text** is honest about what modelrack4j actually stores: it takes
  text, never a parsed config. Nothing is lost in a round trip. The cost is that the user
  must write valid HOCON, and the only feedback is the rejection message.
- **Saving blind** would let two editors, or an editor and an outside edit, overwrite each
  other silently. `storeIfUnchanged(source, expected, text)` turns that into a refusal.

## Decision

`GET /api/config` returns the layer's id and its text; `PUT /api/config` takes
`{expected, text}` and calls `registry.storeIfUnchanged(...)`. The page shows the text in a
textarea, tracks whether it differs from what it loaded, and offers Save and Reload.

Failures are distinguished by status: `409` when the file moved under the editor (the edits
are kept, the message says so), `400` when the configuration was refused, with modelrack4j's
own message shown as-is.

## Consequences

**The raw text reaches the browser, and it is whatever the file holds.** A configuration that
uses `${OPENAI_API_KEY}` exposes only the variable's name — but nothing stops someone pasting
a literal key into the file, and then the editor serves it. Nothing here authenticates, so
anyone who can reach the port can read the configuration and rewrite it. That is why the
server binds `127.0.0.1` by default (`RACKCHAT_HOST` to change it, deliberately): the
exposure is bounded to the machine it runs on. Do not "helpfully" bind `0.0.0.0` to make it
reachable from a phone without solving authentication first.

The editor cannot repair a file it cannot load: if the file on disk is already broken, the
text still loads into the textarea (it is only text), but the registry serving alongside it
is whatever last loaded. Saving remains the way out, since the save validates.

A save updates the running connections and writes the file, and it does **not** fire the
reload listeners — modelrack4j publishes before it writes, so the watcher waking up afterwards
sees an empty diff. Measured, not assumed. The page therefore refetches `/api/connections`
itself after a successful save rather than waiting to be told.

`400` also covers a failure to *write*, because modelrack4j `0.1.0` has one exception type
for both "this text is wrong" and "the file could not be written" — its own ADR-0053 splits
them, but only after this version. A full disk will therefore be reported to the user as a
rejected configuration. This is a reason to bump when `0.2.0` is published; it was not
reproduced here, because this container runs as root and root ignores the directory
permissions that would cause it.
