package io.github.maxtrezzi.rackchat;

import io.github.maxtrezzi.modelrack4j.LlmBundle;
import io.github.maxtrezzi.modelrack4j.LlmConfig;

/**
 * What the browser is told about one configured connection.
 *
 * <p>The API key is deliberately absent. {@link LlmConfig} holds the credential after
 * substitution, so anything that copies a config wholesale into a response leaks it.
 */
public record ConnectionView(
        String name,
        String description,
        String provider,
        String model,
        boolean streaming,
        boolean memory) {

    public static ConnectionView of(LlmBundle bundle) {
        LlmConfig config = bundle.config();
        return new ConnectionView(
                config.name(),
                config.description().orElse(""),
                config.provider(),
                config.modelName(),
                bundle.streamingChatModel().isPresent(),
                // No `memory` block means this connection answers each question on its own.
                // The page says so, because a chat that forgets looks like a bug otherwise.
                bundle.chatMemoryProvider().isPresent());
    }
}
