package io.github.maxtrezzi.rackchat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.github.maxtrezzi.modelrack4j.ConfigValidationException;
import io.github.maxtrezzi.modelrack4j.LlmBundle;
import io.github.maxtrezzi.modelrack4j.LlmRegistry;
import io.github.maxtrezzi.modelrack4j.LlmSnapshot;
import io.github.maxtrezzi.modelrack4j.StaleLayerException;
import io.github.maxtrezzi.modelrack4j.WritableConfigSource;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The HTTP surface of RackChat: a connection list, a streamed chat endpoint, and the static
 * page that uses them.
 *
 * <p>The registry is passed in rather than built here, so a test can hand this method any
 * registry it likes.
 */
public final class RackChatApi {

    private static final Logger log = LoggerFactory.getLogger(RackChatApi.class);

    private RackChatApi() {
    }

    /** One frame of a reply. Sent as JSON so leading spaces and newlines survive SSE framing. */
    public record Token(String text) {
    }

    /** Why a stream ended. {@code error} is null on a clean finish. */
    public record Done(String error) {
    }

    /** The configuration layer as text, with the label modelrack4j knows it by. */
    public record ConfigDocument(String id, String text) {
    }

    /** A save: {@code expected} is the text the page was editing, for the stale check. */
    public record ConfigEdit(String expected, String text) {
    }

    /** A save refused because the layer moved underneath the editor. */
    public record ConfigConflict(String message, String current) {
    }

    /** A save refused because the configuration would not load. */
    public record ConfigRejected(String message) {
    }

    public static Javalin create(LlmRegistry registry, WritableConfigSource configSource, Conversations conversations) {
        return Javalin.create(config -> {
            config.concurrency.useVirtualThreads = true;
            config.staticFiles.add("/public", Location.CLASSPATH);

            config.routes.get("/health", ctx -> ctx.result("ok"));
            config.routes.get("/api/connections", ctx -> ctx.json(connections(registry)));
            config.routes.get("/api/config", ctx -> ctx.json(document(configSource)));
            config.routes.put("/api/config", ctx -> save(ctx, registry, configSource));
            config.routes.sse("/api/chat", client -> {
                client.keepAlive();

                String name = client.ctx().queryParam("connection");
                String message = client.ctx().queryParam("message");
                String conversation = client.ctx().queryParam("conversation");
                if (name == null || name.isBlank() || message == null || message.isBlank()) {
                    finish(client, "connection and message are both required");
                    return;
                }
                if (conversation == null || conversation.isBlank()) {
                    finish(client, "conversation is required");
                    return;
                }

                LlmSnapshot snapshot = registry.snapshot();
                if (!snapshot.contains(name)) {
                    finish(client, "no connection named '" + name + "'");
                    return;
                }
                answer(client, snapshot.get(name), message, conversation, conversations);
            });
        });
    }

    private static ConfigDocument document(WritableConfigSource source) {
        return new ConfigDocument(source.id(), source.text());
    }

    /**
     * Saves edited configuration text through modelrack4j, which validates the whole
     * configuration and publishes it <em>before</em> writing the file — so text that would
     * not load is refused instead of being saved and breaking the next start.
     *
     * <p>The stale check is what makes the editor safe against the file changing underneath
     * it: the page sends back the text it loaded, and a mismatch is a conflict rather than a
     * silent overwrite.
     */
    private static void save(io.javalin.http.Context ctx, LlmRegistry registry, WritableConfigSource source) {
        ConfigEdit edit = ctx.bodyAsClass(ConfigEdit.class);
        if (edit == null || edit.text() == null || edit.expected() == null) {
            ctx.status(400).json(new ConfigRejected("expected and text are both required"));
            return;
        }

        try {
            registry.storeIfUnchanged(source, edit.expected(), edit.text());
            log.info("Configuration saved through the editor; connections are now {}", registry.names());
            ctx.json(document(source));
        } catch (StaleLayerException e) {
            ctx.status(409).json(new ConfigConflict(
                    "The file changed since you loaded it, so nothing was saved.", e.current()));
        } catch (ConfigValidationException e) {
            // modelrack4j 0.1.0 has one exception for both "this text is wrong" and "the file
            // could not be written" — its ADR-0053 splits them, but only after this version.
            // So a genuine write failure will also arrive here and be reported as a rejection.
            ctx.status(400).json(new ConfigRejected(e.getMessage()));
        }
    }

    private static List<ConnectionView> connections(LlmRegistry registry) {
        LlmSnapshot snapshot = registry.snapshot();
        return snapshot.names().stream()
                .sorted()
                .map(name -> ConnectionView.of(snapshot.get(name)))
                .toList();
    }

    /**
     * Answers one question, carrying the conversation's history when the connection has a
     * {@code memory} block.
     *
     * <p>Streams the answer when the connection has a streaming model, and sends it in one
     * frame when it does not — {@code streaming = true} in the configuration is what decides
     * which, since modelrack4j only builds a {@code StreamingChatModel} for those blocks.
     *
     * <p><strong>Memory records completed exchanges only.</strong> The question is sent to the
     * model alongside the history but is written to memory only once an answer comes back, so
     * a failed call leaves no dangling question for the next turn to carry.
     */
    private static void answer(io.javalin.http.sse.SseClient client, LlmBundle bundle,
                               String message, String conversation, Conversations conversations) {
        Optional<ChatMemory> memory = conversations.memoryFor(bundle, conversation);
        UserMessage question = UserMessage.from(message);
        List<ChatMessage> messages = new ArrayList<>();
        memory.ifPresent(remembered -> {
            synchronized (remembered) {
                messages.addAll(remembered.messages());
            }
        });
        messages.add(question);

        Optional<StreamingChatModel> streaming = bundle.streamingChatModel();
        if (streaming.isPresent()) {
            streaming.get().chat(messages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partial) {
                    client.sendEvent("token", new Token(partial));
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    remember(memory, question, response);
                    finish(client, null);
                }

                @Override
                public void onError(Throwable error) {
                    log.warn("Streaming chat failed for '{}'", bundle.name(), error);
                    finish(client, describe(error));
                }
            });
            return;
        }

        try {
            ChatResponse response = bundle.chatModel().chat(messages);
            client.sendEvent("token", new Token(response.aiMessage().text()));
            remember(memory, question, response);
            finish(client, null);
        } catch (RuntimeException e) {
            log.warn("Chat failed for '{}'", bundle.name(), e);
            finish(client, describe(e));
        }
    }

    private static void remember(Optional<ChatMemory> memory, UserMessage question, ChatResponse response) {
        memory.ifPresent(remembered -> {
            synchronized (remembered) {
                remembered.add(question);
                remembered.add(response.aiMessage());
            }
        });
    }

    private static void finish(io.javalin.http.sse.SseClient client, String error) {
        client.sendEvent("done", new Done(error));
        client.close();
    }

    /**
     * Provider exceptions reach us untranslated (modelrack4j ADR-0033), so the message is
     * whatever the provider said. Some carry no message at all, hence the class-name fallback.
     */
    private static String describe(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
