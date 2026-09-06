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

const streamAnswer = (dispatch, { connection, message, conversation }) => {
  const url =
    "/api/chat?connection=" + encodeURIComponent(connection) +
    "&message=" + encodeURIComponent(message) +
    "&conversation=" + encodeURIComponent(conversation)
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

const fetchConfig = (dispatch) => {
  fetch("/api/config")
    .then((response) => {
      if (!response.ok) throw new Error("HTTP " + response.status)
      return response.json()
    })
    .then((document) => dispatch(ConfigLoaded, document))
    .catch((error) => dispatch(ConfigFailed, "Could not read the configuration: " + error.message))
}

const saveConfig = (dispatch, { expected, text }) => {
  fetch("/api/config", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ expected, text }),
  })
    .then((response) => response.json().then((body) => ({ status: response.status, body })))
    .then(({ status, body }) => {
      if (status === 200) dispatch(ConfigSaved, body)
      // 409 means the file moved under the editor; the edits stay, the message explains.
      else dispatch(ConfigFailed, body.message || "The configuration was refused.")
    })
    .catch((error) => dispatch(ConfigFailed, "Could not save: " + error.message))
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
    [streamAnswer, { connection: state.selected, message, conversation: state.conversation }],
    [scrollToEnd],
  ]
}

// The server keeps one history per (conversation, connection), so a new id is a clean start.
const NewConversation = (state) => ({
  ...state,
  conversation: newConversationId(),
  messages: [],
  error: "",
})

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

// --- the configuration editor ----------------------------------------------

const ShowChat = (state) => ({ ...state, tab: "chat", configNote: "" })

const ShowConfig = (state) => [{ ...state, tab: "config", configNote: "" }, [fetchConfig]]

const ConfigLoaded = (state, document) => ({
  ...state,
  configId: document.id,
  configText: document.text,
  // What the save is checked against: the text as it was when we read it.
  configExpected: document.text,
  configSaving: false,
  configNote: "",
})

const ConfigSaved = (state, document) => [
  {
    ...state,
    configText: document.text,
    configExpected: document.text,
    configSaving: false,
    configNote: "Saved. The running connections were updated.",
  },
  [fetchConnections],
]

const ConfigFailed = (state, message) => ({ ...state, configSaving: false, configNote: message })

const EditConfig = (state, event) => ({ ...state, configText: event.target.value })

const SaveConfig = (state) =>
  state.configSaving
    ? state
    : [
        { ...state, configSaving: true, configNote: "" },
        [saveConfig, { expected: state.configExpected, text: state.configText }],
      ]

const ReloadConfig = (state) => [{ ...state, configSaving: false }, [fetchConfig]]

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
  const traits = [current.streaming ? "streamed" : "one answer"]
  // A connection with no `memory` block answers every question on its own. Saying so here is
  // the difference between a documented limit and something that looks broken.
  traits.push(current.memory ? "remembers this conversation" : "no memory: each question stands alone")
  const summary = traits.join(", ")
  return current.description ? current.description + " (" + summary + ")" : summary
}

const chatView = (state) => [
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
]

const configView = (state) => {
  const unsaved = state.configText !== state.configExpected
  return [
    h(
      "p",
      { class: "hint" },
      text(
        state.configId
          ? "Editing " + state.configId + ". Saving validates the whole configuration first: " +
            "if it would not load, nothing is written."
          : "Loading…"
      )
    ),
    state.configNote ? h("p", { class: "error" }, text(state.configNote)) : null,

    h("textarea", {
      class: "editor",
      spellcheck: "false",
      value: state.configText,
      oninput: EditConfig,
      disabled: state.configSaving,
    }),

    h("div", { class: "composer" }, [
      h("span", { class: "hint grow" }, text(unsaved ? "Unsaved changes" : "No changes")),
      h("button", { onclick: ReloadConfig, disabled: state.configSaving }, text("Reload")),
      h(
        "button",
        { onclick: SaveConfig, disabled: state.configSaving || !unsaved },
        text(state.configSaving ? "…" : "Save")
      ),
    ]),
  ]
}

const view = (state) =>
  h("main", {}, [
    h("header", {}, [
      h("h1", {}, text("RackChat")),
      h("div", { class: "tools" }, [
        state.tab === "chat"
          ? h(
              "select",
              {
                onchange: SelectConnection,
                disabled: state.connections.length === 0,
                value: state.selected,
              },
              state.connections.map(connectionOption)
            )
          : null,
        state.tab === "chat"
          ? h(
              "button",
              { onclick: NewConversation, class: "ghost", disabled: state.waiting },
              text("New conversation")
            )
          : null,
        h(
          "button",
          { onclick: state.tab === "chat" ? ShowConfig : ShowChat, class: "ghost" },
          text(state.tab === "chat" ? "Configuration" : "Back to chat")
        ),
      ]),
    ]),

    ...(state.tab === "chat" ? chatView(state) : configView(state)),
  ])

/** crypto.randomUUID needs a secure context; over plain http on a LAN address it is absent. */
const newConversationId = () =>
  crypto.randomUUID ? crypto.randomUUID() : "c-" + Date.now() + "-" + Math.random().toString(36).slice(2)

app({
  init: [
    {
      tab: "chat",
      conversation: newConversationId(),
      connections: [],
      selected: "",
      draft: "",
      messages: [],
      waiting: false,
      error: "",
      configId: "",
      configText: "",
      configExpected: "",
      configSaving: false,
      configNote: "",
    },
    [fetchConnections],
  ],
  view,
  node: document.getElementById("app"),
})
