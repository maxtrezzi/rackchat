# ADR-0008: The frontend ships inside the backend jar, as classpath static files

- **Status:** Accepted
- **Date:** 2026-09-06
- **Supersedes:** —
- **Amends:** —

## Context

ADR-0006 chose Hyperapp, which needs no build step: it is a single ES module, and a page can
import it directly. That removes the usual reason for a separate frontend project (a bundler
with its own dependency tree and output directory), and leaves an open question this
milestone had to answer: where do the page, its stylesheet and the library actually live, and
who serves them?

## Forces

- A top-level `frontend/` directory served from disk keeps the two halves visibly separate,
  but the backend then needs a filesystem path to find it. That path is right when the server
  runs from the repository and wrong everywhere else, including from a packaged jar.
- Putting the files on the backend's classpath (`src/main/resources/public`) makes the
  application one artifact: the jar contains the page it serves, and there is no path to
  configure or get wrong.
- Loading Hyperapp from a content delivery network would avoid the question, but adds a
  runtime dependency on a third-party host for an application that otherwise only talks to
  the model provider, and stops working offline.
- The cost of the classpath route is that "the frontend" lives inside the backend module,
  which reads oddly if a build step is ever added.

## Decision

The page lives at `backend/src/main/resources/public/` — `index.html`, `app.js`,
`styles.css`, and `vendor/hyperapp.js` — and Javalin serves it with
`config.staticFiles.add("/public", Location.CLASSPATH)`. Hyperapp is vendored into the
repository from npm (version 2.0.22), with its MIT licence text beside it, rather than
fetched at runtime.

## Consequences

The backend jar is the whole application: one thing to build, one thing to run, and the page
works offline. Upgrading Hyperapp is a deliberate act — copy in a new file and record the
version — rather than something that happens silently when a CDN updates; the cost is that
nothing reminds the project a newer version exists. If a build step is added later (for
TypeScript, or bundling), its output must land in that same resources directory, so the
serving arrangement does not change. Vendoring makes RackChat a redistributor of Hyperapp,
which is why `vendor/hyperapp-LICENSE.md` sits next to the file and must not be deleted.
