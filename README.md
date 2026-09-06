# RackChat

A web application to chat with large language models (LLM), built on
[modelrack4j](https://github.com/maxtrezzi/modelrack4j) and LangChain4j.

The connections you can talk to are not hard-coded: they come from a modelrack4j
configuration file, one block per connection. The page shows them in a selector, streams the
answer as it arrives, and picks up edits to that file while the server is running — no
restart.

```
llm {
  fast {
    description = "quick answers, streamed"
    provider    = openai
    api-key     = ${OPENAI_API_KEY}
    model-name  = "gpt-5.1"
    streaming   = true
  }
}
```

## Running it

Java 21 and Maven. Copy `backend/rackchat.example.conf`, fill in the environment variables
it names, then:

```bash
cd backend
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
OPENAI_API_KEY=... java -cp "target/classes:$(cat target/cp.txt)" \
  io.github.maxtrezzi.rackchat.Main rackchat.conf
```

Then open <http://localhost:7070/>. `mvn test` runs the tests, which need neither a key nor
a network connection.

## What is here

- `backend/` — the Javalin API and, inside its resources, the Hyperapp page it serves.
- `docs/adr/` — why the project is shaped this way.
- `docs/tasks/` — what is done, what is next, and what each milestone actually found.
- [`AGENTS.md`](AGENTS.md) — the guidance to read before changing anything.

This is a private, personal project.
