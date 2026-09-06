# Open decisions

Items blocked on the owner, not on work. Ask; do not decide unilaterally. An entry here is
a question for the owner, and blocks the code that depends on it rather than inviting a
guess.

None blocking. Candidates raised but not yet decided, for when they become so:

- TypeScript vs. plain JavaScript for the Hyperapp frontend. M1 shipped plain JavaScript
  with no build step; this only reopens if the page grows enough to want types.
- How the modelrack4j configuration is stored long-term (a local file, a database row via
  a custom `ConfigSource` — modelrack4j supports both since its ADR-0042). The browser's
  local storage was explicitly ruled out as the source of truth. M1 reads a file whose path
  comes from an argument or `RACKCHAT_CONFIG`; **M2 needs this settled**, because an editor
  has to know what it is writing to.
- Whether the configuration editor is a text area over the raw HOCON or a form over its
  fields. A text area is honest about what modelrack4j stores (it takes text, never a parsed
  config) and costs nothing to build; a form is friendlier and has to be kept in step with
  the schema by hand.
- Whether a chat keeps history across turns. M1 sends one message with no transcript, so
  `memory` in the configuration currently has no effect on what the model sees.
