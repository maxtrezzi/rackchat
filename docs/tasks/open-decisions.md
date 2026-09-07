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

Also settled by the owner, on 2026-09-07: the registry watches its own layers again
(`watch(true)`, no hand-wired notifier) and hands back the layer the editor writes — reasoning
in ADR-0015, work item M3.5.

Also settled by the owner, on 2026-09-07: RackChat builds against the unreleased modelrack4j
`0.2.0-SNAPSHOT` from the local Maven repository, with the cost that a checkout elsewhere does
not build until modelrack4j is installed — reasoning in ADR-0014, work item M3.4.

Also settled by the owner, on 2026-09-07: the repository is public, under Apache-2.0 — matching
modelrack4j, which this is the first consumer of. Reasoning in ADR-0016, work item M4.

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
- **Packaging.** Running it is still `dependency:build-classpath` plus a `java -cp` line,
  now behind `backend/run.sh` rather than typed out. A shaded jar or `exec:java` would end
  the classpath file itself; nobody has decided which, and the script lowered the pressure to.
- *(none left about the dependency — `0.2.0` reached Maven Central on 2026-09-07 and the
  install-from-source prerequisite went with it; work item M4.1.)*
