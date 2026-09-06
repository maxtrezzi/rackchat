import { h, text, app } from "./vendor/hyperapp.js"

// --- effects ---------------------------------------------------------------
// An effect is [function, props]; Hyperapp calls it with (dispatch, props).

const fetchConnections = (dispatch) => {
  fetch("/api/connections")
    .then((response) => {
      if (!response.ok) throw new Error("HTTP " + response.status)
      return response.json()
    })
    .then((connections) => dispatch(ConnectionsLoaded, connections))
    .catch((error) => dispatch(Failed, "Could not load the connections: " + error.message))
}

const streamAnswer = (dispatch, { connection, message }) => {
  const url =
    "/api/chat?connection=" + encodeURIComponent(connection) + "&message=" + encodeURIComponent(message)
  const source = new EventSource(url)

  source.addEventListener("token", (event) => dispatch(AppendToken, JSON.parse(event.data).text))

  source.addEventListener("done", (event) => {
    // Close before the browser notices the server hung up: an EventSource reconnects on its
    // own, which would ask the model the same question again.
    source.close()
    dispatch(AnswerDone, JSON.parse(event.data).error)
  })

  source.onerror = () => {
    source.close()
    dispatch(AnswerDone, "Lost the connection to the server.")
  }
}

const scrollToEnd = () => {
  requestAnimationFrame(() => {
    const box = document.getElementById("transcript")
    if (box) box.scrollTop = box.scrollHeight
  })
}

// --- actions ---------------------------------------------------------------

const ConnectionsLoaded = (state, connections) => ({
  ...state,
  connections,
  selected: state.selected || (connections.length > 0 ? connections[0].name : ""),
  error: connections.length === 0 ? "No connections are configured in the file." : "",
})

const Failed = (state, error) => ({ ...state, error })

const SelectConnection = (state, event) => ({ ...state, selected: event.target.value })

const EditDraft = (state, event) => ({ ...state, draft: event.target.value })

const Send = (state) => {
  const message = state.draft.trim()
  if (message === "" || state.selected === "" || state.waiting) return state
  return [
    {
      ...state,
      draft: "",
      waiting: true,
      error: "",
      messages: state.messages.concat([
        { who: "you", text: message },
        { who: state.selected, text: "" },
      ]),
    },
    [streamAnswer, { connection: state.selected, message }],
    [scrollToEnd],
  ]
}

const SendOnEnter = (state, event) => (event.key === "Enter" ? Send(state) : state)

const AppendToken = (state, token) => [
  {
    ...state,
    messages: state.messages.map((message, index) =>
      index === state.messages.length - 1 ? { ...message, text: message.text + token } : message
    ),
  },
  [scrollToEnd],
]

const AnswerDone = (state, error) => ({ ...state, waiting: false, error: error || "" })

// --- view ------------------------------------------------------------------

const connectionOption = (connection) =>
  h("option", { value: connection.name }, text(connection.name + " — " + connection.model))

const messageBubble = (message) =>
  h("div", { class: message.who === "you" ? "message mine" : "message theirs" }, [
    h("div", { class: "who" }, text(message.who)),
    h("div", { class: "body" }, text(message.text)),
  ])

const describe = (state) => {
  const current = state.connections.find((connection) => connection.name === state.selected)
  if (!current) return ""
  const streaming = current.streaming ? "streamed" : "one answer"
  return current.description ? current.description + " (" + streaming + ")" : streaming
}

const view = (state) =>
  h("main", {}, [
    h("header", {}, [
      h("h1", {}, text("RackChat")),
      h(
        "select",
        {
          onchange: SelectConnection,
          disabled: state.connections.length === 0,
          value: state.selected,
        },
        state.connections.map(connectionOption)
      ),
    ]),

    h("p", { class: "hint" }, text(describe(state))),
    state.error ? h("p", { class: "error" }, text(state.error)) : null,

    h("div", { id: "transcript", class: "transcript" }, state.messages.map(messageBubble)),

    h("div", { class: "composer" }, [
      h("input", {
        type: "text",
        placeholder: "Ask something, then press Enter",
        value: state.draft,
        oninput: EditDraft,
        onkeydown: SendOnEnter,
        disabled: state.waiting || state.connections.length === 0,
      }),
      h(
        "button",
        { onclick: Send, disabled: state.waiting || state.connections.length === 0 },
        text(state.waiting ? "…" : "Send")
      ),
    ]),
  ])

app({
  init: [
    { connections: [], selected: "", draft: "", messages: [], waiting: false, error: "" },
    [fetchConnections],
  ],
  view,
  node: document.getElementById("app"),
})
