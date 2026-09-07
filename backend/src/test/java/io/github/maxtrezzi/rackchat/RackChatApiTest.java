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
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These tests build real provider objects from a real configuration file, with a fake key.
 * That works offline because modelrack4j builds a bundle without calling the provider — the
 * key is only used at the first request, which no test here makes.
 */
class RackChatApiTest {

    private static final String CONFIG = """
            llm {
              fast {
                description = "streamed"
                provider    = openai
                api-key     = "sk-not-a-real-key"
                model-name  = "gpt-5.1"
                streaming   = true
              }
              plain {
                provider   = openai
                api-key    = "sk-not-a-real-key"
                model-name = "gpt-5.1"
              }
            }
            """;

    /** The same configuration with one block added — written out, not patched with replace(). */
    private static final String CONFIG_WITH_ONE_MORE = """
            llm {
              fast {
                description = "streamed"
                provider    = openai
                api-key     = "sk-not-a-real-key"
                model-name  = "gpt-5.1"
                streaming   = true
              }
              plain {
                provider   = openai
                api-key    = "sk-not-a-real-key"
                model-name = "gpt-5.1"
              }
              added {
                provider   = openai
                api-key    = "sk-not-a-real-key"
                model-name = "gpt-5.1"
              }
            }
            """;

    @TempDir
    Path directory;

    private Path config;
    private WritableConfigSource source;
    private LlmRegistry<Void> registry;
    private Javalin app;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void startApp() throws IOException {
        config = Files.writeString(directory.resolve("test.conf"), CONFIG);
        source = ConfigSource.ofWritableFile(config);
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
    void healthEndpointReturnsOk() throws Exception {
        HttpResponse<String> response = get("/health");

        assertEquals(200, response.statusCode());
        assertEquals("ok", response.body());
    }

    @Test
    void connectionsAreListedAlphabeticallyWithTheirStreamingFlag() throws Exception {
        HttpResponse<String> response = get("/api/connections");
        String body = response.body();

        assertEquals(200, response.statusCode());
        assertTrue(body.contains("\"name\":\"fast\""), body);
        assertTrue(body.contains("\"name\":\"plain\""), body);
        assertTrue(body.indexOf("\"fast\"") < body.indexOf("\"plain\""), body);
        assertTrue(body.contains("\"description\":\"streamed\""), body);
        assertTrue(body.contains("\"streaming\":true"), body);
        assertTrue(body.contains("\"streaming\":false"), body);
    }

    /**
     * The credential must never reach the browser. {@code LlmConfig} holds it after
     * substitution, so this guards against anyone serialising a config or a bundle directly.
     */
    @Test
    void connectionsNeverCarryTheApiKey() throws Exception {
        String body = get("/api/connections").body();

        assertFalse(body.contains("sk-not-a-real-key"), body);
        assertFalse(body.toLowerCase().contains("apikey"), body);
        assertFalse(body.toLowerCase().contains("api-key"), body);
    }

    @Test
    void anUnknownConnectionEndsTheStreamWithAnError() throws Exception {
        HttpResponse<String> response = sse("/api/chat?connection=nope&message=hello&conversation=c1");
        String body = response.body();

        assertEquals(200, response.statusCode());
        assertTrue(body.contains("event: done"), body);
        assertTrue(body.contains("no connection named 'nope'"), body);
    }

    @Test
    void aMissingMessageEndsTheStreamWithAnError() throws Exception {
        String body = sse("/api/chat?connection=fast").body();

        assertTrue(body.contains("event: done"), body);
        assertTrue(body.contains("required"), body);
    }

    /**
     * Without the Accept header Javalin's SSE handler does nothing at all: the request gets
     * an empty 200 with {@code Content-Type: text/plain} and the handler never runs. A
     * browser's EventSource always sends it, so only a hand-written client can get this
     * wrong — as the first version of these two tests did.
     */
    @Test
    void anSseRequestWithoutTheAcceptHeaderGetsNothing() throws Exception {
        HttpResponse<String> response = get("/api/chat?connection=fast&message=hello");

        assertEquals(200, response.statusCode());
        assertEquals("", response.body());
    }

    @Test
    void thePageAndItsVendoredLibraryAreServed() throws Exception {
        HttpResponse<String> page = get("/");
        HttpResponse<String> hyperapp = get("/vendor/hyperapp.js");

        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("<title>RackChat</title>"), page.body());
        assertEquals(200, hyperapp.statusCode());
        assertTrue(hyperapp.body().contains("export var app"), "vendored hyperapp.js is not the ESM build");
    }

    // --- the configuration editor ------------------------------------------------------

    @Test
    void theConfigurationIsServedAsTextWithItsLayerId() throws Exception {
        HttpResponse<String> response = get("/api/config");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\\\"sk-not-a-real-key\\\""), response.body());
        assertTrue(response.body().contains(config.toString()), response.body());
    }

    @Test
    void savingValidTextRewritesTheFileAndUpdatesTheRegistry() throws Exception {
        HttpResponse<String> response = put("/api/config", edit(CONFIG, CONFIG_WITH_ONE_MORE));

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(Files.readString(config).contains("added {"), "the file was not rewritten");
        assertTrue(registry.names().contains("added"), "the registry did not pick up the new block");
        assertTrue(get("/api/connections").body().contains("\"name\":\"added\""));
    }

    /**
     * The ordering is the whole reason modelrack4j has {@code store}: validate and publish
     * first, write second. Text that would not load must never reach the file, or the next
     * start fails on something the editor accepted.
     */
    @Test
    void savingBrokenTextChangesNeitherTheFileNorTheRegistry() throws Exception {
        String before = Files.readString(config);

        HttpResponse<String> response = put("/api/config",
                edit(CONFIG, "llm { broken { provider = nosuchprovider, api-key = \"x\", model-name = \"m\" } }"));

        assertEquals(400, response.statusCode(), response.body());
        assertEquals(before, Files.readString(config), "the file was changed by a rejected save");
        assertTrue(registry.names().contains("fast"), "the live registry lost its connections");
    }

    /**
     * A save can fail for a reason the editor cannot fix. modelrack4j 0.2.0 raises a separate
     * exception for a layer it cannot write, and that is answered apart from rejected text:
     * a {@code 400} tells the user to correct what is in front of them, which would be a lie
     * here.
     */
    @Test
    void aSaveThatCannotWriteTheFileIsNotTheEditorsMistake() throws Exception {
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(directory);
        // The write goes through a temporary file beside the target, so it is the directory
        // that has to be writable, not the configuration file.
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            HttpResponse<String> response = put("/api/config", edit(CONFIG, CONFIG_WITH_ONE_MORE));

            assertEquals(500, response.statusCode(), response.body());
            assertEquals(CONFIG, Files.readString(config), "the file was changed by a failed save");
        } finally {
            Files.setPosixFilePermissions(directory, permissions);
        }
    }

    @Test
    void savingAgainstStaleTextIsRefusedAsAConflict() throws Exception {
        HttpResponse<String> response = put("/api/config",
                edit("this is not what the file holds", CONFIG));

        assertEquals(409, response.statusCode(), response.body());
        assertTrue(response.body().contains("changed since you loaded it"), response.body());
    }

    private static String edit(String expected, String text) {
        return "{\"expected\":" + quote(expected) + ",\"text\":" + quote(text) + "}";
    }

    /** Minimal JSON string escaping — enough for the configuration text these tests send. */
    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private HttpResponse<String> put(String path, String body) throws IOException, InterruptedException {
        return send(request(path)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return send(request(path).GET().build());
    }

    /** An SSE request, with the header a browser's EventSource sends and Javalin requires. */
    private HttpResponse<String> sse(String path) throws IOException, InterruptedException {
        return send(request(path).header("Accept", "text/event-stream").GET().build());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + path))
                .timeout(Duration.ofSeconds(10));
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
