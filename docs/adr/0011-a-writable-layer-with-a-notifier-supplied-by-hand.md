# ADR-0011: A writable layer with the file notifier supplied by hand

- **Status:** Accepted
- **Date:** 2026-09-06
- **Supersedes:** —
- **Amends:** —

## Context

M1 built the registry with `configFiles(List.of(path))` and `watch(true)`: file layers, and
the library's own watcher. ADR-0010 needs `store()`, which takes a `WritableConfigSource`,
and a writable source can only reach the registry through `sources(...)`.

In modelrack4j `0.1.0` those two are mutually exclusive. Probed, not assumed:

```
sources(writable) + watch(true): REFUSED -> ConfigValidationException:
  watch(true) watches configuration files, and this registry has none —
  its layers were given through sources(...).
  Supply a ChangeNotifier, or call reload() when the configuration changes.
```

The refusal is a defect in that version — the layer *is* a file — and modelrack4j fixed it
after `0.1.0` (its ADR-0050). RackChat cannot wait for that: `0.2.0` is not published.

## Forces

- **Drop the watcher.** The editor would work and outside edits would stop being noticed,
  losing the behaviour M1 verified live. Cheapest, and the biggest loss.
- **Poll `reload()` on a timer.** Works, but reimplements what the library already does, and
  picks a period that is either wasteful or slow.
- **Supply the notifier the error message suggests.** `FileChangeNotifier.of(List<Path>,
  Duration)` is public in `0.1.0`, and `Builder.notifier(...)` accepts it. Probed: the
  registry builds, an outside edit fires `onReload` and changes `names()`, and `store()` still
  works — with no listener fired by the store itself, because the publish happens before the
  write.

## Decision

Build the registry from a writable source plus an explicit notifier:

```java
WritableConfigSource source = ConfigSource.ofWritableFile(config);
LlmRegistry registry = LlmRegistry.builder()
        .sources(List.of(source))
        .notifier(FileChangeNotifier.of(List.of(config), Duration.ofMillis(300)))
        .build();
```

## Consequences

RackChat gets both halves — an editor and hot reload — on the published version, at the price
of wiring by hand what `watch(true)` used to do. **Do not "simplify" this back to
`configFiles(...)` with `watch(true)`**: it compiles, it looks tidier, and it takes the
editor away, because the layer stops being writable. When RackChat moves to a modelrack4j
that has ADR-0050 in it, `watch(true)` will accept file-backed sources and this wiring can
collapse — that is a change to make deliberately, with the probe re-run, not a cleanup to
assume.

The 300 ms debounce is the same figure the library's own watcher uses, chosen to match rather
than measured here.
