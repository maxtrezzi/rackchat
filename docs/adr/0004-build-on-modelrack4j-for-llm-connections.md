# ADR-0004: Build on modelrack4j for LLM connection management, not a bespoke integration

- **Status:** Accepted
- **Date:** 2026-09-05
- **Supersedes:** —
- **Amends:** —

## Context

RackChat needs to hold one or more named connections to large language models (LLM) —
different providers, different models, different credentials — and let a user switch
between them in a chat page, as well as edit which connections exist. The owner already
maintains modelrack4j, a library built for exactly this: layered HOCON configuration files
resolve to named, ready-to-use bundles of LangChain4j objects, with validated hot reload and
a `store()` API to write a configuration layer back safely.

modelrack4j's own guidance names its first consumer as "the owner's own application,
developed in parallel" — RackChat is that application.

## Forces

- Writing connection management directly against LangChain4j inside RackChat would
  duplicate what modelrack4j already does (parsing, per-name diffing, capability
  validation across providers, safe reload) and would drift from it over time.
- Depending on modelrack4j ties RackChat's release cadence to modelrack4j's: a breaking
  change there (the project is still `0.x` and reserves the right to break in a minor)
  reaches RackChat directly.
- modelrack4j is designed around exactly this shape of consumer — the alternative (a
  from-scratch connection layer) would be reinventing it with less scrutiny.

## Decision

RackChat depends on modelrack4j (`io.github.maxtrezzi:modelrack4j-*`) for everything about
naming, configuring, validating and hot-reloading LLM connections. The backend holds an
`LlmRegistry`; the configuration editor in the frontend writes back through modelrack4j's
`store()` API (validated before it is written), never through a raw file write. RackChat
does not re-implement anything modelrack4j already provides — a gap found here is reported
against modelrack4j, not worked around locally.

## Consequences

RackChat's own configuration format for LLM connections *is* modelrack4j's HOCON schema —
there is no translation layer. A modelrack4j credential (API key) is never sent to the
browser; it is read only on the backend, consistent with modelrack4j's own
`LlmConfig.toString()` redaction rule. Upgrading modelrack4j is a normal dependency bump,
but because the library is pre-1.0, it should be treated as a task worth its own entry in
`docs/tasks/`, not a silent version-number edit.
