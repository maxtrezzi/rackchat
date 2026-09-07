# Open decisions

Items blocked on the owner, not on work. Ask; do not decide unilaterally. An entry here is
a question for the owner, and blocks the code that depends on it rather than inviting a
guess.

None blocking. Settled on 2026-09-06 by the owner: the configuration stays in the file it is
already read from (an argument or `RACKCHAT_CONFIG`), and the editor is a textarea over the
raw HOCON — both implemented in M2, reasoning in ADR-0010.

Also settled by the owner, on 2026-09-07: switching connection mid-conversation
carries the history across, by seeding the new connection's memory once from the one the
conversation was last using — reasoning in ADR-0013, work item M3.3.

Also settled by the owner, on 2026-09-07: RackChat builds against the unreleased modelrack4j
`0.2.0-SNAPSHOT` from the local Maven repository, with the cost that a checkout elsewhere does
not build until modelrack4j is installed — reasoning in ADR-0014, work item M3.4.

Candidates raised but not yet decided, for when they become so:

- TypeScript vs. plain JavaScript for the Hyperapp frontend. M1 shipped plain JavaScript
  with no build step; this only reopens if the page grows enough to want types.
- **Whether history should survive a restart.** M3 settled where memory lives (the server,
  one per conversation and connection — ADR-0012) but it is held in memory only, so
  restarting loses every conversation while the page still shows the transcript. Persisting
  it is a new decision, not an oversight.
- **Authentication.** There is none, and the editor serves the configuration's raw text. The
  server binds `127.0.0.1` for that reason. Anything that makes RackChat reachable from
  another machine needs this settled first.
- **Packaging.** Running it is still `dependency:build-classpath` plus a `java -cp` line. A
  shaded jar or `exec:java` would end that; nobody has decided which.
- **Whether to collapse ADR-0011's hand-wired notifier into `watch(true)`.** modelrack4j
  `0.2.0` watches the file layers given to `sources(...)` (its ADR-0050), so the
  `FileChangeNotifier` built by hand — and the second list of paths it needs — is no longer
  required. It works and is tested, so replacing it buys simplicity and nothing else; that is
  a decision, not a tidy-up.
- **When to go back to a published modelrack4j.** M3.4 took `0.2.0-SNAPSHOT` from the local
  repository (ADR-0014), which is one property in `backend/pom.xml` and a build that only
  works on this machine. Moving to `0.2.0` on Maven Central is the same property, and the
  question is only when it is published.
