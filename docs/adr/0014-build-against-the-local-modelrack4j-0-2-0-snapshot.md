# ADR-0014: Build against the local modelrack4j 0.2.0-SNAPSHOT

- **Status:** Accepted
- **Date:** 2026-09-07
- **Supersedes:** —
- **Amends:** —

## Context

RackChat has depended on modelrack4j `0.1.0` from Maven Central since M0.1, and three of the
shapes in this repository exist because of what that version cannot do:

- the file notifier is wired by hand, because `watch(true)` is refused on a registry built
  from `sources(...)` ([ADR-0011](0011-a-writable-layer-with-a-notifier-supplied-by-hand.md));
- a save that fails because the *file* could not be written is answered `400`, the same as
  text that would not load, because one exception type covers both
  ([ADR-0010](0010-edit-the-raw-hocon-in-a-textarea.md));
- RackChat cannot start on a configuration that names no connection, so "start empty and fill
  it in from the editor" is not a route — the page that holds the editor is only reachable
  once something has loaded.

`0.2.0` fixes all three. It is not published, and the owner is the person publishing it: the
working copy on this machine is `0.2.0-SNAPSHOT`, and `mvn install` puts it in the local
repository. The owner asked for RackChat to build against that.

## Forces

- **A released dependency is reproducible.** `0.1.0` resolves from Maven Central on any
  machine, which is what makes a fresh checkout buildable by anyone. A `-SNAPSHOT` resolved
  from `~/.m2` is buildable *here*, and nowhere else until modelrack4j is cloned and installed.
- **A snapshot is a moving target.** `mvn install` in the modelrack4j working copy changes what
  RackChat compiles against, with no version to say so. A build that worked yesterday can fail
  today with nothing changed in this repository — a cost that lands as confusion unless it is
  written down.
- **Waiting for the release keeps three workarounds alive**, and one of them is not a
  workaround but a missing feature: with no empty rack, RackChat's own documentation has to
  tell a first-time reader that the editor cannot be their starting point.
- **RackChat is modelrack4j's first consumer**, which is the other half of this. Building
  against the snapshot is how the library's unreleased API gets exercised by something that is
  not its own test suite, and that feedback is worth more before `0.2.0` is published than
  after.
- **The move is reversible in one line.** The version is a single property in `backend/pom.xml`.
  Nothing else in the build refers to it.

## Decision

`modelrack4j.version` is `0.2.0-SNAPSHOT`, resolved from the local Maven repository. Building
RackChat requires modelrack4j to be installed there first (`mvn install` in its working copy),
and this is stated in `docs/running-locally.md` rather than left to be discovered at the first
`mvn compile`.

Two of `0.2.0`'s changes are taken up in the same move, because they are what the current code
already wanted:

- **A failed write is answered apart from rejected text.** `ConfigAccessException` is not a
  subclass of `ConfigValidationException`, so `/api/config` answers `400` for text that would
  not load and `500` for a file it could not write — the editor is only told to fix something
  when there is something in it to fix.
- **An empty configuration is valid**, so RackChat starts with no connections, the page says
  none are configured, and the editor is reachable. Starting from nothing and filling it in
  from the page now works.

One is deliberately **not** taken up: ADR-0011's hand-wired notifier stays, even though
`watch(true)` now accepts layers from `sources(...)`. Collapsing it removes a mechanism that
works and is tested, which is a decision of its own rather than a detail of a version bump.

RackChat returns to a released version when `0.2.0` is published; that is a change of one
property and is not a decision anybody needs to revisit this one for.

## Consequences

**A fresh checkout no longer builds on its own.** Anyone starting from this repository needs
the modelrack4j working copy and an `mvn install` first. This is the cost that pays for
everything else here, and it is temporary — but "temporary" is exactly what a note in the
running instructions has to carry, since nothing in the build says it.

**A rebuild upstream can break this one without a commit here.** modelrack4j's API is allowed
to move inside a snapshot, and the version in `backend/pom.xml` will not change when it does.
A compile failure after an upstream `mvn install` is that, not a defect in RackChat.

**A configuration that loaded under `0.1.0` can now be refused.** `0.2.0` rejects a key its
schema does not know, where the old version ignored it in silence — a misspelling that used to
do nothing is now a startup failure that names the key, the layer and the line. That is an
improvement, and it will still look like a regression the first time someone meets it.

**modelrack4j's types carry a parameter now.** `LlmRegistry`, `LlmBundle` and `LlmSnapshot` are
generic in whatever a `CustomPropertiesHandler` produces. RackChat registers none, so it builds
an `LlmRegistry<Void>` and takes `<?>` everywhere the type is irrelevant. A raw type still
compiles and must not be used: it erases the generic members of the whole class, including the
`Optional<ReloadChange>` a `store` returns.

**M3.2's finding is obsolete, and the entry stays as it is.** It recorded that RackChat could
not start unconfigured, with the reasoning for why solving it on this side was not worth the
component. Solving it upstream is what happened instead; the record of the choice is worth
keeping.
