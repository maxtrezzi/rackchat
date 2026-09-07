# ADR-0012: Memory lives on the server, one per conversation and connection

- **Status:** Accepted — the connection switch amended by ADR-0013
- **Date:** 2026-09-06
- **Supersedes:** —
- **Amends:** —

## Context

Until M3 each request sent one message and nothing else, so `memory` in the configuration
changed nothing about what the model saw. The owner asked for a real chat — questions and
answers that build on each other. modelrack4j already builds a `ChatMemoryProvider` per
bundle from the `memory` block, which is the piece that decides how much history a connection
sees; what was missing was who keeps a history and what it is keyed by.

## Forces

- **The browser could send the whole transcript** with every question, leaving the server
  stateless. But then the `memory` block would still change nothing: the window that decides
  what the model sees lives in the provider, on the server. It would also let the page decide
  what the model is told, which is the wrong place for it.
- **One shared history per conversation**, replayed into whichever connection is asked, reads
  the way most chat tools behave. It has a cost that only shows up later: a `token-window`
  memory on a provider whose estimator is remote bills a call per eviction check
  (modelrack4j's ADR-0021 and ADR-0027), and rebuilding a shared transcript for each request
  would re-run that estimation over the whole history every turn.
- **One history per (conversation, connection)** matches how the library is built: a memory
  carries its connection's policy and accumulates incrementally, so nothing is re-estimated.
  The cost is a surprise — switch connection mid-conversation and the new one starts fresh.
- **RackChat could supply a default memory** where the configuration asks for none. That would
  make every connection a chat, and would also mean the application quietly overriding a
  configuration that said nothing — the opposite of ADR-0004.

## Decision

The server keeps one `ChatMemory` per (conversation, connection), obtained once from the
bundle's `ChatMemoryProvider` and reused. The browser generates a conversation id and sends it
with every question; "New conversation" makes a new one.

A connection with no `memory` block has no memory here either, and the page says so under the
selector rather than leaving it to be discovered.

Memory records **completed exchanges only**: the question is sent to the model alongside the
history, and written to memory together with the answer once one arrives. A failed call leaves
no trace.

## Consequences

The `memory` block finally means what it says, including its window: with
`max-messages = 2`, the third question arrives with only the second exchange in front of it —
tested, against a fake provider that reports the messages it received.

**Switching connection starts that connection's own history.** Ask one connection something,
switch, and the second one does not know what was said. This is the intended behaviour, not a
bug, and it follows from each connection carrying its own eviction policy.

**A configuration change does not reshape a conversation already in progress.** The memory was
built from the provider the bundle had at the time, so changing `max-messages` applies to
conversations started afterwards. Nothing is discarded silently, which is the trade taken: the
alternative — dropping the history whenever the block is edited — would lose a conversation
because someone changed a timeout. Connections *removed* by a reload do have their histories
dropped, since they can never be reached again.

Histories are held in memory only: restarting the server loses them, and the page's transcript
then describes turns the model no longer remembers. The map is bounded (200 histories, least
recently used dropped) so it cannot grow without limit.
