# ADR-0016: Publish the repository under Apache-2.0

- **Status:** Accepted
- **Date:** 2026-09-07
- **Supersedes:** —
- **Amends:** —

## Context

The repository has been private since M0, and `AGENTS.md` has carried a line saying so, with
the instruction to adjust it if that ever changed. The owner has decided it should be public.

Three things about this project were built on the assumption of a single reader and now have
to answer to strangers:

- **Nothing here authenticates.** The configuration tab serves the configuration file's raw
  text, which is why the server binds `127.0.0.1`
  ([ADR-0010](0010-edit-the-raw-hocon-in-a-textarea.md)). Private, that was a note to self;
  public, it is a property of the product that someone will meet without having read the ADR.
- **The build does not resolve on its own.** `modelrack4j` is not on Maven Central at the
  version this depends on ([ADR-0014](0014-build-against-the-local-modelrack4j-0-2-0-snapshot.md)),
  so a clone does not compile until modelrack4j is installed from source.
- **Nothing has ever verified the build anywhere but this machine.** There is no CI, and the
  claim that the suite needs no key and no network is asserted rather than checked.

RackChat is modelrack4j's first consumer, and modelrack4j is already public under Apache-2.0.
Publishing this is what makes that claim inspectable: the library's README says what it is
for, and this is the application that does it.

## Forces

- **Apache-2.0 against MIT.** MIT is shorter and RackChat is a small application, so the
  patent grant and the `NOTICE` convention are weight it does not obviously need. Against
  that: this repository exists to be read next to modelrack4j, which is Apache-2.0, and two
  repositories by the same author under different licences raises a question that has no
  interesting answer. Matching costs nothing and explains itself.
- **A licence, once published, cannot be withdrawn from what it covered.** That argues for
  deciding it before the first public commit rather than after, which is the only reason this
  is settled now instead of when someone asks.
- **Publishing makes the whole history public, not the current tree.** Anything ever committed
  is readable afterwards, so a link, a path or a credential that has since been deleted is
  still there. This is the one part of going public that cannot be corrected later, and the
  only moment it is cheap to fix is while the repository is still private.
- **A public repository that does not build is worse than a private one that does.** The
  modelrack4j prerequisite is not going away today; what can go away is a reader discovering
  it at the first `mvn compile` rather than in the instructions.
- **A narrow project attracts wide pull requests.** RackChat has a shape recorded across
  fifteen ADRs, and the expensive outcome is a large unsolicited contribution that contradicts
  one of them — expensive for whoever wrote it, and unpleasant to decline.
- **The untested path is a liability and an invitation.** No part of this has ever exchanged a
  message with a real model. Published, that stops being a private embarrassment and becomes
  the single most useful thing a reader with a key can do.

## Decision

The repository is public, licensed **Apache-2.0**, with `LICENSE` and `NOTICE` at the root and
licence, `scm`, `url` and developer metadata in `backend/pom.xml`. The vendored Hyperapp keeps
its own MIT licence beside it in `public/vendor/`, and `NOTICE` names it — a bundled
dependency's licence travels with the file, not with the project's.

Four things follow from publishing, and are part of this decision rather than consequences of
it:

- **No link that only the owner can open goes into this repository** — not in a commit
  message, an issue, or a document. The history is rewritten before publication to remove the
  session-link trailers it carried; `Co-Authored-By` stays, because it names a role rather
  than a private URL.
- **CI runs on every push and pull request to `main`**, and one of its jobs builds with the
  provider credential variables scrubbed. The suite is claimed to need no key and no network;
  that job is what turns the claim into something checked, so a test which quietly grows a
  dependency on a key fails there rather than for the first person who clones without one.
- **`CONTRIBUTING.md` asks for an issue first**, points at `docs/adr/` for "why is it like
  this", and says plainly that the scope is narrow.
- **The security posture is stated in the README as a property, not a caveat.** Loopback bind,
  no authentication, and the configuration's raw text served to whoever reaches the page.

The three-artifact workflow ([ADR-0001](0001-record-decisions-as-adrs.md)) is unchanged, and
its division is exactly what makes publication safe: `brainstorm/` stays git-ignored and local,
`docs/adr/` is the distilled trail that was always written to be read by someone else.

## Consequences

**The licence is now irrevocable for everything published under it.** A later change of mind
applies to later versions only; what is out is out under Apache-2.0.

**The CI has a step that exists to be deleted.** Both jobs clone modelrack4j and install it
before building. The day `0.2.0` is on Maven Central, that step and the matching paragraph in
`docs/running-locally.md` go, and nothing else changes. Leaving it in place after that point
would mean CI silently testing against a working copy of `main` rather than the release the
build asks for.

**Issues can now arrive about a path nobody has run.** The live model call is the untested one,
and a bug report about it should be read as likely rather than surprising — that expectation is
in `AGENTS.md` and the README both, so the first such report is not a shock.

**The `AGENTS.md` guidance is now public too**, including its account of what is load-bearing
and where the sharp edges are. That is the intent: it is the file that makes the rest of the
repository legible, and it was written to be read.

**Nothing about publication makes RackChat safe to expose.** `RACKCHAT_HOST` still moves the
bind address, there is still no authentication, and the editor still serves the configuration's
raw text. A public repository will make that combination easier to find, which is an argument
for the README stating it, not for the ADR softening it.
