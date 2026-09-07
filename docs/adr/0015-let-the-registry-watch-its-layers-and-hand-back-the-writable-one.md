# ADR-0015: Let the registry watch its layers, and hand back the one to write

- **Status:** Accepted
- **Date:** 2026-09-07
- **Supersedes:** —
- **Amends:** ADR-0011

## Context

[ADR-0011](0011-a-writable-layer-with-a-notifier-supplied-by-hand.md) built the registry from
`sources(writable)` and passed a `FileChangeNotifier` by hand, because modelrack4j `0.1.0`
refused `watch(true)` on a registry built that way — and `store()` needs a source from
`sources(...)`, so the editor could not be given up. The hand-wiring was the only shape that
gave both, and it cost a second list naming the same file the source already names.

[ADR-0014](0014-build-against-the-local-modelrack4j-0-2-0-snapshot.md) moved to `0.2.0`, where
`watch(true)` watches the file layers whichever builder method supplied them (its ADR-0050),
and where `LlmRegistry.sources()` gives an application its layers back. modelrack4j's own
reference says both plainly: `watch(true)` "is the usual way" to get the watcher, building one
by hand is "only for a file the library cannot recognise as one", and `sources()` is there to
"find the layer to write instead of keeping the reference beside the registry".

The owner asked for RackChat to use the library at its best. This is the part of that which
changes decided structure rather than code.

## Forces

- **The hand-wired notifier works and is tested.** Nothing is broken, so the case for changing
  it is not a bug — it is that RackChat now carries a mechanism the library provides, and the
  next reader has to be told why. That reason expired with `0.1.0`.
- **The second path list is a duplicate that can drift.** `FileChangeNotifier.of(List.of(config), …)`
  names the same file `ConfigSource.ofWritableFile(config)` names. Two spellings of one path is
  a defect waiting for a second layer to be added to only one of them.
- **`watch(true)` and `notifier(...)` cannot both be set**, so this is one or the other, not a
  gradual move.
- **The debounce becomes the library's default** (300 ms — the same value the hand-wired
  notifier passed) instead of a number written here. Losing the explicit constant is a small
  loss of visibility, against a number RackChat has no reason to choose.
- **Carrying the writable source beside the registry lets the two disagree.** `create` took a
  registry and a `WritableConfigSource` as separate arguments, so a caller could hand it a
  source the registry was never built from; the store would then be refused at runtime, for a
  reason nothing in the signature hints at. Asking the registry cannot produce that pair.

## Decision

The registry watches its own layers, and is asked for the layer to write.

```java
LlmRegistry<Void> registry = LlmRegistry.builder()
        .sources(List.of(ConfigSource.ofWritableFile(config)))
        .watch(true)
        .build();
```

`RackChatApi.create(registry, conversations)` takes no source: it finds the highest-precedence
writable layer among `registry.sources()` and refuses to start without one. RackChat builds
exactly one, so "highest-precedence" is a rule for a case that does not exist yet rather than
a choice being made now.

**A store still drops the histories of the connections it removed, and must.** modelrack4j
answers a `store` with what changed and fires no reload listener — the caller made the change,
so it is told rather than notified. The listener that drops histories therefore runs for an
edit made outside the page and never for one made in it, which is why `/api/config` uses the
returned `ReloadChange` itself.

## Consequences

One list of paths instead of two, one thread started by the library instead of one wired here,
and the argument that could disagree with the registry is gone from the public method.

**Hot reload is now the library's, so a failure in it is reported upstream rather than fixed
here.** That is the point of using the shipped watcher — its symlink and ConfigMap handling,
its re-registration after a watched directory is deleted, and its debounce are things RackChat
would otherwise be reimplementing badly.

**RackChat can no longer be run with a registry whose layers are not files.** Nothing did that,
and `watch(true)` refuses to build such a registry rather than starting a watcher over nothing.
A configuration held somewhere else — a database row, a remote service — would need
`notifier(...)` back, which is the shape ADR-0011 described and this ADR does not delete.

**ADR-0011's reasoning stays worth reading.** What it decided about `sources` and `store()` is
still how this works; only the reason for supplying the notifier by hand expired. The
`0.1.0` probes it records are the evidence for why the code looked like that for as long as it
did.
