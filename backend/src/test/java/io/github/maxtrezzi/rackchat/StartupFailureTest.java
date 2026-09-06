package io.github.maxtrezzi.rackchat;

import io.github.maxtrezzi.modelrack4j.ConfigSource;
import io.github.maxtrezzi.modelrack4j.ConfigValidationException;
import io.github.maxtrezzi.modelrack4j.LlmRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a first run is told when it goes wrong. The failure here is the real one modelrack4j
 * raises for an unset variable, not a stand-in: the point of the test is that the sentence a
 * user sees names the variable they have to set.
 */
class StartupFailureTest {

    @TempDir
    Path directory;

    @Test
    void anUnsetVariableIsExplainedWithoutAStackTrace() throws IOException {
        Path config = Files.writeString(directory.resolve("unset.conf"), """
                llm {
                  fast {
                    provider   = openai
                    api-key    = ${A_VARIABLE_NOBODY_HAS_SET}
                    model-name = "gpt-5.1"
                  }
                }
                """);

        ConfigValidationException failure = assertThrows(ConfigValidationException.class,
                () -> LlmRegistry.builder()
                        .sources(List.of(ConfigSource.ofWritableFile(config)))
                        .watch(false)
                        .build());

        String explained = Main.explain(failure);

        assertTrue(explained.contains("A_VARIABLE_NOBODY_HAS_SET"),
                "the message must name the variable to set: " + explained);
        assertTrue(explained.contains("environment variable"), explained);
        assertFalse(explained.contains("\tat "), "a stack frame reached the message: " + explained);
        assertFalse(explained.contains("io.github.maxtrezzi.modelrack4j.ConfigLoader"),
                "an internal class name reached the message: " + explained);
    }

    /** A cause the message already quotes must not be printed a second time. */
    @Test
    void aCauseAlreadyFoldedIntoTheMessageIsNotRepeated() {
        Exception cause = new IllegalStateException("the underlying reason");
        Exception failure = new IllegalStateException("it broke: the underlying reason", cause);

        assertEquals("it broke: the underlying reason", Main.explain(failure));
    }

    @Test
    void aCauseThatAddsSomethingIsShown() {
        Exception failure = new IllegalStateException("it broke", new IllegalStateException("disk is full"));

        assertTrue(Main.explain(failure).contains("caused by: disk is full"), Main.explain(failure));
    }
}
