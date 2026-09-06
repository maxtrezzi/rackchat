package io.github.maxtrezzi.rackchat;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.github.maxtrezzi.modelrack4j.LlmBundle;
import io.github.maxtrezzi.modelrack4j.LlmRegistry;
import io.github.maxtrezzi.modelrack4j.LlmSnapshot;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public static Javalin create(LlmRegistry registry) {
        return Javalin.create(config -> {
            config.concurrency.useVirtualThreads = true;
            config.staticFiles.add("/public", Location.CLASSPATH);

            config.routes.get("/health", ctx -> ctx.result("ok"));
            config.routes.get("/api/connections", ctx -> ctx.json(connections(registry)));
            config.routes.sse("/api/chat", client -> {
                client.keepAlive();

                String name = client.ctx().queryParam("connection");
                String message = client.ctx().queryParam("message");
                if (name == null || name.isBlank() || message == null || message.isBlank()) {
                    finish(client, "connection and message are both required");
                    return;
                }

                LlmSnapshot snapshot = registry.snapshot();
                if (!snapshot.contains(name)) {
                    finish(client, "no connection named '" + name + "'");
                    return;
                }
                answer(client, snapshot.get(name), message);
            });
        });
    }

    private static List<ConnectionView> connections(LlmRegistry registry) {
        LlmSnapshot snapshot = registry.snapshot();
        return snapshot.names().stream()
                .sorted()
                .map(name -> ConnectionView.of(snapshot.get(name)))
                .toList();
    }

    /**
     * Streams the answer when the connection has a streaming model, and sends it in one
     * frame when it does not — {@code streaming = true} in the configuration is what decides
     * which, since modelrack4j only builds a {@code StreamingChatModel} for those blocks.
     */
    private static void answer(io.javalin.http.sse.SseClient client, LlmBundle bundle, String message) {
        Optional<StreamingChatModel> streaming = bundle.streamingChatModel();
        if (streaming.isPresent()) {
            streaming.get().chat(message, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partial) {
                    client.sendEvent("token", new Token(partial));
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
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
            client.sendEvent("token", new Token(bundle.chatModel().chat(message)));
            finish(client, null);
        } catch (RuntimeException e) {
            log.warn("Chat failed for '{}'", bundle.name(), e);
            finish(client, describe(e));
        }
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
