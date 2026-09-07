# ADR-0013: Carry the conversation across a connection switch by seeding the new memory

- **Status:** Accepted
- **Date:** 2026-09-07
- **Supersedes:** —
- **Amends:** ADR-0012

## Context

[ADR-0012](0012-memory-lives-on-the-server-one-per-conversation-and-connection.md) put one
`ChatMemory` behind each (conversation, connection) pair and said, in as many words, that
switching connection mid-conversation starts that connection's own history. It listed the
consequence as intended behaviour and `docs/tasks/open-decisions.md` kept it as a candidate to
revisit "if it turns out to feel wrong in use".

It does. Changing model in the middle of a chat is one of the reasons to have a rack of them —
ask the cheap connection, move to the strong one when the question turns hard — and today the
strong one arrives knowing nothing. The page keeps showing the whole transcript while it
happens, so nothing on screen says the model was not told: the amnesia surfaces as an answer
that ignores what was just agreed.

What has not changed is the constraint underneath ADR-0012: the window that decides what a
model sees belongs to that connection's `memory` block, built by modelrack4j into the bundle's
`ChatMemoryProvider`. Whatever carries a conversation across a switch has to go through the
receiving connection's own policy rather than around it.

## Forces

- **Leaving it alone** keeps the model exactly as ADR-0012 described and costs nothing. Its
  price is the one above, and it is paid silently — the transcript on the page is not evidence
  of what the model was told.
- **One shared history per conversation, replayed into whichever connection is asked**, was
  rejected in ADR-0012 and is rejected again for the same reason: a `token-window` memory over
  a provider with a remote estimator bills a call per eviction check (modelrack4j's ADR-0021
  and ADR-0027), and rebuilding a shared transcript per request re-runs that estimation over
  the whole history every turn.
- **A separate full transcript per conversation, kept beside the memories**, would seed a new
  connection with everything ever said, including turns the previous connection's window had
  already dropped. It buys fidelity for a second store with its own lifetime, its own bound
  and its own restart story, and it hands a fresh window an entire history to estimate in one
  go. What one connection retained is already a defensible answer to "the conversation so
  far", and it needs no new store.
- **Seeding the new memory once, from the memory the conversation was last using**, spends the
  eviction pass a single time per (conversation, connection) instead of every turn, and leaves
  each memory accumulating incrementally afterwards — the property that made the pair the unit
  in the first place.
- **Making it optional**, a checkbox or a configuration key, keeps both behaviours. But "New
  conversation" already is the clean start, and a second control that answers the same
  question would only ask the owner to hold two ways of starting over in their head.

## Decision

When a conversation reaches a connection it has not used before, the new memory is **seeded**
with the conversation's current history before it is used.

- `Conversations` tracks, per conversation, the memory that conversation last wrote to. That
  is the source: a switch carries from where the conversation actually was, not from some
  earlier connection it passed through.
- On creating a memory for a (conversation, connection) pair that has none, the source's
  messages are replayed into it **in order, through the new memory's own `add`**, so the
  receiving connection's eviction policy shapes what survives. The messages are not copied as
  a list, and nothing arrives past the window its `memory` block allows.
- Seeding happens once, at creation. Switching back to a connection that already has a memory
  finds it as it was left and adds nothing: memories are not kept in step with each other.
- A connection with no `memory` block still has no memory here, and receives nothing — there
  is nowhere to put it. Equally, a conversation whose last turn ran on such a connection has
  no source to carry, so the next connection starts empty.

"New conversation" remains the way to ask a model something with no history in front of it.

## Consequences

Changing connection mid-chat now continues the thread instead of restarting it, up to what the
receiving connection's `memory` block keeps.

**The carry costs one eviction pass, at the switch.** On a `message-window` memory that is
nothing; on a `token-window` memory with a remote estimator it bills that estimation once per
(conversation, connection), where the rejected shared-transcript design would have billed it
every turn. This is the cost that was bought deliberately, and it is bounded by the number of
connections a conversation touches.

**What is carried is what the source connection retained.** A connection with a two-message
window carries two messages, whatever the page still shows above them. Keeping a conversation
whole across switches is therefore a property of the windows configured on the connections it
uses, not something RackChat can promise on top of them.

**A connection's history can now contain text no configuration of its own produced** — another
model's answers, shaped by another provider. That is the intent, and it is also the reason
this stays plain-text conversation: RackChat sends no tools, and an `AiMessage` carrying tool
calls replayed into a provider that never issued them would not be answerable. Adding tools
means revisiting what may be carried.

**Replaying through `add` is load-bearing and must not be "simplified" into copying the source
list.** A copy would put messages in front of a model that its own `memory` block excludes,
which is precisely the promise ADR-0012 exists to keep.

The rest of ADR-0012 stands unchanged: memory objects still come from the bundle and never
from RackChat, the unit is still one per (conversation, connection), a memory is still written
only when an answer comes back, and histories are still held in memory only — a restart still
loses them, whether or not they were carried.
