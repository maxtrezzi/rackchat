# Open decisions

Items blocked on the owner, not on work. Ask; do not decide unilaterally. An entry here is
a question for the owner, and blocks the code that depends on it rather than inviting a
guess.

None blocking. Settled on 2026-09-06 by the owner: the configuration stays in the file it is
already read from (an argument or `RACKCHAT_CONFIG`), and the editor is a textarea over the
raw HOCON — both implemented in M2, reasoning in ADR-0010.

Candidates raised but not yet decided, for when they become so:

- TypeScript vs. plain JavaScript for the Hyperapp frontend. M1 shipped plain JavaScript
  with no build step; this only reopens if the page grows enough to want types.
- **Whether a chat keeps history across turns.** Each request sends one message with no
  transcript, so `memory` in the configuration currently changes nothing about what the model
  sees. This is the most likely thing to be mistaken for a bug — it is a gap, not a defect,
  and closing it is a decision about where the transcript lives (browser, server, or a
  LangChain4j `AiServices` with the bundle's `ChatMemoryProvider`).
- **Authentication.** There is none, and the editor serves the configuration's raw text. The
  server binds `127.0.0.1` for that reason. Anything that makes RackChat reachable from
  another machine needs this settled first.
- **Packaging.** Running it is still `dependency:build-classpath` plus a `java -cp` line. A
  shaded jar or `exec:java` would end that; nobody has decided which.
- Moving to modelrack4j `0.2.0` when it is published, which would let ADR-0011's hand-wired
  notifier collapse back into `watch(true)` and split a failed write from an invalid
  configuration (its ADR-0050 and ADR-0053).
