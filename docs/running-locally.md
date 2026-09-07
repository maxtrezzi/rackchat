# Running RackChat locally

Every command on this page was run from a fresh `git clone` on 2026-09-06, on Linux with
OpenJDK 21.0.10 and Maven 3.9.11. The output shown is the output those commands produced.

## What you need

- **Java 21 or newer.** The backend is compiled for 21 (ADR-0007).
- **Maven 3.9 or newer.**
- **modelrack4j `0.2.0-SNAPSHOT` in your local Maven repository.** RackChat builds against the
  unreleased version, which is not on Maven Central, so it has to be installed from source
  first (ADR-0014). This step disappears when `0.2.0` is published:

  ```bash
  git clone https://github.com/maxtrezzi/modelrack4j
  cd modelrack4j && mvn -DskipTests install
  ```

  That is modelrack4j's own build, and the one step on this page whose output is not shown
  here.
- **An API key** for at least one provider — OpenAI, Anthropic, Google Gemini or GLM. If you
  do not have one yet, jump to [Trying it without an account](#trying-it-without-an-account):
  the application runs and the chat works, answered by a fake provider.

Check the two versions before anything else. A wrong Java version fails in a way that is not
obviously about Java:

```bash
java -version    # openjdk version "21.0.10" ...
mvn -v           # Apache Maven 3.9.11 ...
```

## 1. Clone and build

```bash
git clone https://github.com/maxtrezzi/rackchat
cd rackchat/backend
mvn verify
```

`verify` compiles and runs the 27 tests. They need no key and no network connection, so a
failure here is a real failure, not a missing credential.

## 2. Write your configuration

RackChat has no configuration format of its own: the file is a
[modelrack4j](https://github.com/maxtrezzi/modelrack4j) configuration, and every block under
`llm` becomes one connection you can pick in the page (ADR-0004).

```bash
cp rackchat.example.conf rackchat.conf
```

Then open `rackchat.conf` and keep only the connections you have keys for. A minimal one:

```hocon
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

Three things about that block are worth knowing before you edit it:

- **`api-key = ${OPENAI_API_KEY}` reads an environment variable**, and the form without `?`
  is mandatory: if the variable is not set, RackChat refuses to start and says which one is
  missing. That is deliberate — the optional form `${?VAR}` would start happily and fail later
  as an authentication error at your first question.
- **`memory` is what makes it a conversation.** Without that block the connection still works,
  but each question is answered on its own, and the page says so under the selector
  (ADR-0012).
- **`streaming = true` is what makes the answer appear word by word.** Without it the answer
  arrives in one piece.

`rackchat.conf` is in `.gitignore`, so your own file is never committed.

## 3. Run it

The build produces `target/rackchat-backend-0.1.0-SNAPSHOT.jar`, but that jar holds only
RackChat's own classes — there is no packaging step yet that bundles the dependencies. So the
run goes through a classpath file that Maven writes for you:

```bash
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt

OPENAI_API_KEY=sk-your-real-key \
  java -cp "target/classes:$(cat target/cp.txt)" \
  io.github.maxtrezzi.rackchat.Main rackchat.conf
```

You only need the first command again after changing the dependencies in `pom.xml`.

You should see:

```
[main] INFO io.javalin.Javalin - Listening on http://127.0.0.1:7070/
[main] INFO io.github.maxtrezzi.rackchat.Main - RackChat is reading /…/rackchat.conf and knows 1 connection(s): [fast]
```

Now open **<http://127.0.0.1:7070/>**.

Stop it with `Ctrl-C`.

### Where the configuration file is looked up

The path is resolved from the directory you run the command in. Three ways to give it, in
order of precedence:

1. the first argument — `… Main /home/me/rackchat.conf`
2. the `RACKCHAT_CONFIG` environment variable
3. nothing, in which case `rackchat.conf` in the current directory is used

### The other two settings

| Variable | Default | What it does |
|---|---|---|
| `RACKCHAT_PORT` | `7070` | The port to listen on |
| `RACKCHAT_HOST` | `127.0.0.1` | The address to bind |

**Think before changing `RACKCHAT_HOST`.** The configuration tab serves the file's raw text,
because you cannot edit what you cannot see — and nothing in RackChat asks who you are. On
`127.0.0.1` that exposure stops at your own machine. Binding anywhere else puts your
configuration, and any key written literally in it, on the network. See
[ADR-0010](adr/0010-edit-the-raw-hocon-in-a-textarea.md).

## 4. What you can do in the page

- **Ask a question.** Type and press Enter. The answer streams in if the connection has
  `streaming = true`.
- **Follow up.** With a `memory` block, the next question carries the conversation.
  **New conversation** starts a clean one.
- **Switch connection** in the selector. The conversation goes with you: the connection you
  switch to is told what has been said so far, as far as its own `memory` block keeps it
  ([ADR-0013](adr/0013-carry-the-conversation-across-a-connection-switch.md)). A connection
  with no `memory` block is handed nothing, and there is nothing to carry away from it either.
  **New conversation** — not a different connection — is what starts over.
- **Edit the configuration** in the second tab. Save validates the whole configuration before
  writing anything: if the text would not load, nothing is saved and the message tells you
  why. A connection you add appears in the selector without a restart.
- **Edit the file in your editor instead.** The change is picked up about a second later, also
  without a restart.

## Trying it without an account

The test sources carry a fake provider that answers with a description of the messages it
received. It is the only way to see the chat work with no key at all — and it makes memory
visible, which is otherwise invisible from outside.

```bash
cd rackchat/backend
mvn -q test-compile

cat > rackchat.conf <<'EOF'
llm {
  echo {
    description = "a fake provider, for trying the app without any account"
    provider    = echo
    api-key     = "unused"
    model-name  = "echo-1"
    memory { type = message-window, max-messages = 20 }
  }
}
EOF

mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java -cp "target/classes:target/test-classes:$(cat target/cp.txt)" \
  io.github.maxtrezzi.rackchat.Main rackchat.conf
```

`target/test-classes` on the classpath is what makes `provider = echo` resolvable.

Ask two questions in the page and you will see the memory working:

```
you   first
echo  saw 1: user=first
you   second
echo  saw 3: user=first|ai|user=second
```

The second answer reports three messages: your first question, the answer to it, and your new
question.

## When something goes wrong

Startup failures print a message and stop with exit code `1`. A *stack trace* means something
unexpected, which is worth reporting rather than working around.

**"RackChat cannot start: the configuration was rejected."** — followed by the reason. The
usual one is an unset variable:

```
  A mandatory substitution is unresolved after merging all layers. Set the environment
  variable, or override the value in a higher-precedence layer: /…/rackchat.conf: 27:
  Could not resolve substitution to a value: ${OPENAI_API_KEY}
```

Set that variable in the shell you start RackChat from, or change the connection to a provider
whose key you do have. The same message shape covers any other rejected configuration — an
unknown `provider`, a `memory` block a provider cannot support — with a different reason.

**"RackChat cannot start: Port already in use…"** — something else has the port, quite
possibly an earlier RackChat you did not stop. Javalin logs its own `Failed to start Javalin`
line just before; the last line is the one to read. Use another port:

```bash
RACKCHAT_PORT=7099 java -cp "target/classes:$(cat target/cp.txt)" \
  io.github.maxtrezzi.rackchat.Main rackchat.conf
```

**"Cannot read the configuration file: …"** — the path does not exist from where you are. The
message prints the absolute path RackChat tried, which is usually enough to see the mistake.

**The page loads but the selector is empty** — the configuration has no `llm` blocks, or they
were all rejected. The terminal says which.

**A question fails with a provider error** — the message in the page comes from the provider
itself, unchanged. An authentication error means the key is wrong or the account has no access
to that `model-name`; model names are not checked against a list, so a typo shows up only
here.

**The connection answers but forgets everything** — that connection has no `memory` block. The
line under the selector says so.

## One thing nobody has checked

No part of this project has ever exchanged a message with a real model: it was built on a
machine with no provider key and no route to the providers. Every other path was exercised,
including a provider failure arriving cleanly in the page. If the first real answer misbehaves,
that is the untested ground, and worth reporting rather than working around.
