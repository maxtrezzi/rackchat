# RackChat

A web application to chat with large language models (LLM), built on
[modelrack4j](https://github.com/maxtrezzi/modelrack4j) and LangChain4j.

The connections you can talk to are not hard-coded: they come from a modelrack4j
configuration file, one block per connection. The page shows them in a selector, streams the
answer as it arrives, and lets you edit that file in a second tab. Edits take effect without
a restart — whether you make them in the page or in the file itself. A change that would not
load is refused before anything is written.

A connection with a `memory` block holds a conversation: follow-up questions carry what was
said before, within the window the configuration sets. One without it answers each question on
its own, and the page says so under the selector.

```
llm {
  fast {
    description = "quick answers, streamed"
    provider    = openai
    api-key     = ${OPENAI_API_KEY}
    model-name  = "gpt-5.1"
    streaming   = true
    memory { type = message-window, max-messages = 20 }
  }
}
```

## Running it

Java 21 and Maven. Copy `backend/rackchat.example.conf`, fill in the environment variables
it names, then:

```bash
cd backend
mvn verify
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
OPENAI_API_KEY=... java -cp "target/classes:$(cat target/cp.txt)" \
  io.github.maxtrezzi.rackchat.Main rackchat.conf
```

Then open <http://127.0.0.1:7070/>. `mvn test` runs the tests, which need neither a key nor
a network connection.

**[`docs/running-locally.md`](docs/running-locally.md) is the full version**: what to check
first, what each setting does, how to try the whole application with no API key at all, and
what the common failures look like.

The server listens on `127.0.0.1` only. That is deliberate: the configuration tab serves the
file's raw text, and nothing here asks who you are. See
[ADR-0010](docs/adr/0010-edit-the-raw-hocon-in-a-textarea.md) before changing `RACKCHAT_HOST`.

## What is here

- `backend/` — the Javalin API and, inside its resources, the Hyperapp page it serves.
- [`docs/running-locally.md`](docs/running-locally.md) — how to run it from a fresh checkout.
- `docs/adr/` — why the project is shaped this way.
- `docs/tasks/` — what is done, what is next, and what each milestone actually found.
- [`AGENTS.md`](AGENTS.md) — the guidance to read before changing anything.

This is a private, personal project.
