# ADR-0007: Maven and Java 21 for the backend build

- **Status:** Accepted
- **Date:** 2026-09-06
- **Supersedes:** —
- **Amends:** —

## Context

ADR-0005 chose Javalin as the backend web framework but left the build tool and the Java
version open (`docs/tasks/open-decisions.md`). RackChat is a separate repository from
modelrack4j and is not bound to modelrack4j's Java 17 floor (ADR-0019 in modelrack4j is a
constraint on that project's published artifacts, not on its consumers).

## Forces

- **Maven vs. Gradle.** modelrack4j already uses Maven, so Maven means one build tool
  across both repositories the owner works in, with no new tooling to learn. Gradle was
  not raised as a real alternative in this discussion — the owner asked for Maven directly.
- **Java 17 vs. Java 21 vs. later.** Both 17 and 21 are LTS releases. Java 21 adds virtual
  threads (`Thread.ofVirtual()`, `ExecutorService` support), which matter here because the
  chat endpoint streams a response from an LLM provider for the duration of the request —
  code written in a straightforward blocking style scales without hand-managing a thread
  pool. Java 17 would work too, and would match modelrack4j's floor exactly, but that
  match has no technical benefit here: RackChat calls modelrack4j as a published library
  dependency, not as source, so the two projects' language levels are independent.

## Decision

Build the backend with Maven, targeting Java 21 (`maven.compiler.release=21`).

## Consequences

The backend can use virtual threads for request handling once that is needed, without a
second ADR just to permit it. It cannot share a language-level floor with modelrack4j if
that library is ever vendored or forked locally — not a live concern today, since it is
consumed as a Maven Central artifact. The development environment already carries OpenJDK 21.0.10 (Ubuntu build) and
Maven 3.9.11, so no toolchain setup is needed beyond the `pom.xml` itself.
