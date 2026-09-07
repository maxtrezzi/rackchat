package io.github.maxtrezzi.rackchat;

import io.github.maxtrezzi.modelrack4j.ConfigSource;
import io.github.maxtrezzi.modelrack4j.LlmRegistry;
import io.github.maxtrezzi.modelrack4j.WritableConfigSource;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether a follow-up question carries the conversation — and whether a change of connection
 * carries it too (ADR-0013) — checked against a fake provider that answers with the messages
 * it was given ({@link EchoProviderFactory}). A real provider cannot answer this question
 * offline, and "the model remembered" is not observable any other way.
 */
class ChatMemoryTest {

    private static final String CONFIG = """
            llm {
              remembers {
                provider   = echo
                api-key    = "unused"
                model-name = "echo-1"
                memory { type = message-window, max-messages = 10 }
              }
              forgets {
                provider   = echo
                api-key    = "unused"
                model-name = "echo-1"
              }
              streamed {
                provider   = echo
                api-key    = "unused"
                model-name = "echo-1"
                streaming  = true
                memory { type = message-window, max-messages = 10 }
              }
              shortMemory {
                provider   = echo
                api-key    = "unused"
                model-name = "echo-1"
                memory { type = message-window, max-messages = 2 }
              }
            }
            """;

    @TempDir
    Path directory;

    private LlmRegistry registry;
    private Javalin app;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void startApp() throws IOException {
        Path config = Files.writeString(directory.resolve("memory.conf"), CONFIG);
        WritableConfigSource source = ConfigSource.ofWritableFile(config);
        registry = LlmRegistry.builder().sources(List.of(source)).watch(false).build();
        app = RackChatApi.create(registry, source, new Conversations()).start(0);
    }

    @AfterEach
    void stopApp() {
        if (app != null) {
            app.stop();
        }
        if (registry != null) {
            registry.close();
        }
    }

    @Test
    void aFollowUpQuestionCarriesTheEarlierTurns() throws Exception {
        assertTrue(ask("remembers", "one", "c1").contains("saw 1: user=one"), "the first turn should stand alone");

        String second = ask("remembers", "two", "c1");

        // The second question arrives with the first exchange in front of it.
        assertTrue(second.contains("saw 3: user=one|ai|user=two"), second);
    }

    @Test
    void aDifferentConversationStartsEmpty() throws Exception {
        ask("remembers", "one", "c1");

        assertTrue(ask("remembers", "two", "c2").contains("saw 1: user=two"));
    }

    @Test
    void switchingConnectionCarriesTheConversation() throws Exception {
        ask("remembers", "one", "c1");

        // Same conversation, different connection: the new memory is seeded with what the
        // conversation already has, so the second connection is told about the first turn.
        assertTrue(ask("streamed", "two", "c1").contains("saw 3: user=one|ai|user=two"));
    }

    /**
     * The carry goes <em>through</em> the receiving connection's eviction rather than around
     * it: four messages replayed into a {@code max-messages = 2} window leave the last
     * exchange, not the whole history.
     */
    @Test
    void theReceivingWindowLimitsWhatIsCarried() throws Exception {
        ask("remembers", "one", "c1");
        ask("remembers", "two", "c1");

        String carried = ask("shortMemory", "three", "c1");

        assertTrue(carried.contains("saw 3: user=two|ai|user=three"), carried);
        assertTrue(!carried.contains("user=one"), "the window must apply to a carried history: " + carried);
    }

    @Test
    void aConnectionWithoutAMemoryBlockIsHandedNothing() throws Exception {
        ask("remembers", "one", "c1");

        // There is nowhere to put a history on a connection that keeps none.
        assertTrue(ask("forgets", "two", "c1").contains("saw 1: user=two"));
    }

    @Test
    void aTurnOnAConnectionWithoutMemoryLeavesNothingToCarry() throws Exception {
        ask("remembers", "one", "c1");
        ask("forgets", "two", "c1");

        // The conversation's last exchange was on a connection that kept nothing, so that is
        // what the next one is given.
        assertTrue(ask("streamed", "three", "c1").contains("saw 1: user=three"));
    }

    @Test
    void switchingBackFindsThatHistoryAsItWasLeft() throws Exception {
        ask("remembers", "one", "c1");
        ask("streamed", "two", "c1");

        String back = ask("remembers", "three", "c1");

        // Seeding happens once, at creation: the memories do not stay in step afterwards, so
        // this one still holds its own turn and no second copy of it.
        assertTrue(back.contains("saw 3: user=one|ai|user=three"), back);
        assertTrue(!back.contains("user=two"), "memories must not be kept in step: " + back);
    }

    @Test
    void aConnectionWithoutAMemoryBlockNeverAccumulates() throws Exception {
        ask("forgets", "one", "c1");

        assertTrue(ask("forgets", "two", "c1").contains("saw 1: user=two"),
                "a connection with no memory block must not accumulate history");
    }

    @Test
    void aStreamedConnectionRemembersTheSameWay() throws Exception {
        ask("streamed", "one", "c9");

        assertTrue(ask("streamed", "two", "c9").contains("saw 3:"));
    }

    /**
     * The window comes from the configuration: {@code max-messages = 2} keeps one exchange,
     * so by the third question the first one is gone and only the second is still in front
     * of it.
     */
    @Test
    void theConfiguredWindowLimitsWhatTheModelSees() throws Exception {
        ask("shortMemory", "one", "c1");
        ask("shortMemory", "two", "c1");

        String third = ask("shortMemory", "three", "c1");

        assertTrue(third.contains("saw 3: user=two|ai|user=three"), third);
        assertTrue(!third.contains("user=one"), "the oldest turn should have been evicted: " + third);
    }

    @Test
    void aChatWithoutAConversationIsRefused() throws Exception {
        String body = sse("/api/chat?connection=remembers&message=hello").body();

        assertTrue(body.contains("conversation is required"), body);
    }

    @Test
    void connectionsSayWhetherTheyRemember() throws Exception {
        String body = send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + "/api/connections"))
                .GET().build()).body();

        assertTrue(body.contains("\"name\":\"remembers\",\"description\":\"\",\"provider\":\"echo\""
                + ",\"model\":\"echo-1\",\"streaming\":false,\"memory\":true"), body);
        assertTrue(body.contains("\"name\":\"forgets\"") && body.contains("\"memory\":false"), body);
    }

    /** Sends one question and returns the text the fake model answered with. */
    private String ask(String connection, String message, String conversation) throws Exception {
        HttpResponse<String> response = sse("/api/chat?connection=" + connection
                + "&message=" + message + "&conversation=" + conversation);
        assertEquals(200, response.statusCode());
        return response.body();
    }

    private HttpResponse<String> sse(String path) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + path))
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build());
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
