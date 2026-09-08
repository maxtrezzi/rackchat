# Contributing

Thanks for looking. RackChat is a small application with a narrow scope and a shape recorded
across sixteen ADRs, so the most useful thing you can do before writing code is **open an
issue**.

## Open an issue first

Please do that for anything beyond an obvious typo — a bug report, a question, or a sketch of
a change you are considering. A short conversation costs you ten minutes; a large unsolicited
pull request that contradicts a decision already made costs us both far more, and is
unpleasant to decline.

That is the only real rule here.

**One exception, where the answer is already yes: the look of the page.** `styles.css` is 161
lines of plain CSS that nobody has designed, and improving it needs no discussion first — as
long as it stays a stylesheet the browser loads directly. A CSS framework or a highlighting
editor for the configuration tab would end the no-build-step frontend
([ADR-0008](docs/adr/0008-the-frontend-ships-inside-the-backend-jar.md)), and that one is worth
an issue before you write it.

## The most useful bug report

**No part of this project has ever exchanged a message with a real model.** It was built on a
machine with no provider key and no route to the providers, so every path was exercised except
the one where tokens actually arrive. What *was* seen is the failure half: a provider error
reaching the browser as a clean `done` frame instead of a hang.

If you run this with a real key and the token path misbehaves, that is the untested ground.
A report about it is worth more than one about anything else here.

## Before proposing a change

[`docs/adr/`](docs/adr/README.md) records every design decision with the alternatives that
were rejected and why. If a change would contradict one, that is not a blocker — but the
discussion starts from the reasoning already there rather than from scratch. Some of them will
answer "why is it like this?" faster than an issue will:

- [ADR-0010](docs/adr/0010-edit-the-raw-hocon-in-a-textarea.md) — why the configuration editor
  is a textarea over raw HOCON, why a save validates before it writes, and why the server binds
  loopback.
- [ADR-0012](docs/adr/0012-memory-lives-on-the-server-one-per-conversation-and-connection.md)
  and [ADR-0013](docs/adr/0013-carry-the-conversation-across-a-connection-switch.md) — why
  memory comes from the configuration rather than from a default here, and what a connection
  switch does to a conversation.
- [ADR-0004](docs/adr/0004-build-on-modelrack4j-for-llm-connections.md) — why connections come
  from [modelrack4j](https://github.com/maxtrezzi/modelrack4j) rather than a bespoke
  integration. A change to what a configuration *can say* usually belongs in that project, not
  this one.

[`AGENTS.md`](AGENTS.md) is the short version of what is load-bearing, and worth reading before
changing anything.

## Building

Java 21 and Maven; every dependency resolves from Maven Central. See
[`docs/running-locally.md`](docs/running-locally.md) for the full version, including how to run
the whole application with no API key at all.

```bash
git clone https://github.com/maxtrezzi/rackchat
cd rackchat/backend
mvn verify    # compile and the 28 tests
```

**The build must pass with no keys and no network.** Building a bundle never calls the
provider, which is what keeps the suite offline; a CI job runs it with the credential
variables scrubbed specifically to catch a test that quietly grows a dependency on one.

The frontend has no build step: it is plain JavaScript served from
`backend/src/main/resources/public/`, importing a vendored Hyperapp
([ADR-0008](docs/adr/0008-the-frontend-ships-inside-the-backend-jar.md)).

## Pull requests

- One branch per change, never committed straight to `main`
  ([ADR-0003](docs/adr/0003-one-feature-branch-per-task.md)).
- A change that settles a design question closes by adding an ADR — copy
  [`docs/adr/0000-template.md`](docs/adr/0000-template.md), take the next number, and add a row
  to the index. A short ADR beats none.
- Commit messages describe what the code now does and why it is shaped that way, not how the
  work went.

## Licence

By contributing you agree that your contribution is licensed under the Apache License 2.0, the
same terms as the rest of the project ([ADR-0016](docs/adr/0016-publish-the-repository-under-apache-2-0.md)).
