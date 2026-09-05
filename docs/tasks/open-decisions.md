# Open decisions

Items blocked on the owner, not on work. Ask; do not decide unilaterally. An entry here is
a question for the owner, and blocks the code that depends on it rather than inviting a
guess.

None yet. Candidates raised in conversation but not yet decided, for when they become
blocking:

- Build tool for the backend (Maven, matching modelrack4j, or something else) and which
  Java version to target — RackChat is a separate repository and is not bound to
  modelrack4j's Java 17 floor.
- TypeScript vs. plain JavaScript for the Hyperapp frontend.
- How the modelrack4j configuration is stored long-term (a local file, a database row via
  a custom `ConfigSource` — modelrack4j supports both since ADR-0042 in modelrack4j). The
  browser's local storage was explicitly ruled out as the source of truth; a decision on
  the real storage is still open.
