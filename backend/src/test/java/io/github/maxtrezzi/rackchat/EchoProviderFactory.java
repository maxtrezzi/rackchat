package io.github.maxtrezzi.rackchat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.moderation.ModerationModel;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.spi.ProviderFactory;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A provider that answers with a description of what it was asked, so a test can see exactly
 * which messages reached the model.
 *
 * <p>This is the only way the memory behaviour can be checked here: the real providers need a
 * key and a network, and "the model remembered" is not observable from the outside without
 * one. Registered through {@code META-INF/services} in test resources, so modelrack4j finds it
 * with the same {@code ServiceLoader} lookup it uses for the real ones.
 */
public final class EchoProviderFactory implements ProviderFactory {

    @Override
    public String providerId() {
        return "echo";
    }

    @Override
    public TokenEstimation tokenEstimation() {
        // No estimator, so token-window memory is refused for this provider - message-window
        // is what the tests use, and it needs no counting.
        return TokenEstimation.ABSENT;
    }

    @Override
    public void validate(LlmConfig config) {
    }

    @Override
    public ChatModel createChatModel(LlmConfig config) {
        // Not a lambda: every method on ChatModel is a default method, so there is no single
        // abstract one to implement.
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                return ChatResponse.builder().aiMessage(AiMessage.from(describe(request))).build();
            }
        };
    }

    @Override
    public Optional<StreamingChatModel> createStreamingChatModel(LlmConfig config) {
        if (!config.streaming()) {
            return Optional.empty();
        }
        return Optional.of(new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                AiMessage answer = AiMessage.from(describe(request));
                handler.onPartialResponse(answer.text());
                handler.onCompleteResponse(ChatResponse.builder().aiMessage(answer).build());
            }
        });
    }

    @Override
    public Optional<ModerationModel> createModerationModel(LlmConfig config) {
        return Optional.empty();
    }

    @Override
    public Optional<TokenCountEstimator> createTokenCountEstimator(LlmConfig config) {
        return Optional.empty();
    }

    /** Renders the request as {@code saw N: user=…|ai|user=…}, which is what the tests assert on. */
    private static String describe(ChatRequest request) {
        String rendered = request.messages().stream()
                .map(EchoProviderFactory::render)
                .collect(Collectors.joining("|"));
        return "saw " + request.messages().size() + ": " + rendered;
    }

    /**
     * An answer is rendered as a bare {@code ai}, with its text left out **on purpose**. An
     * earlier answer is itself one of these descriptions, so including the text would nest
     * each turn inside the next — and then any assertion of the form "the body still mentions
     * the first question" is true whether or not that question is still in the window. That
     * cost two wrong test failures before the rendering was changed.
     */
    private static String render(ChatMessage message) {
        if (message instanceof UserMessage user) {
            return "user=" + user.singleText();
        }
        if (message instanceof AiMessage) {
            return "ai";
        }
        return message.type().toString();
    }
}
