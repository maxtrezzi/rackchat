package io.github.maxtrezzi.rackchat;

import io.github.maxtrezzi.modelrack4j.ConfigSource;
import io.github.maxtrezzi.modelrack4j.FileChangeNotifier;
import io.github.maxtrezzi.modelrack4j.LlmRegistry;
import io.github.maxtrezzi.modelrack4j.WritableConfigSource;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static final String DEFAULT_CONFIG = "rackchat.conf";
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 7070;

    private Main() {
    }

    public static void main(String[] args) {
        Path config = configPath(args);
        if (!Files.isReadable(config)) {
            System.err.println("Cannot read the configuration file: " + config.toAbsolutePath());
            System.err.println("Copy rackchat.example.conf, fill in your keys, and pass its path as the");
            System.err.println("first argument or in RACKCHAT_CONFIG.");
            System.exit(1);
        }

        // The layer is writable so the editor can store through it, and the notifier is
        // supplied by hand because watch(true) refuses a registry built from sources(...) in
        // modelrack4j 0.1.0 - the two together are what give an editor and hot reload at once.
        WritableConfigSource source = ConfigSource.ofWritableFile(config);
        LlmRegistry registry = LlmRegistry.builder()
                .sources(List.of(source))
                .notifier(FileChangeNotifier.of(List.of(config), Duration.ofMillis(300)))
                .build();

        Conversations conversations = new Conversations();

        // One listener doing both jobs: onReload takes a single consumer, so registering a
        // second one here would be a coin flip between adding and replacing.
        registry.onReload(change -> {
            log.info("Configuration reloaded: {} added, {} updated, {} removed",
                    change.added(), change.updated(), change.removed());
            conversations.forget(change.removed());
        });
        registry.onReloadFailure(failure -> log.warn("Configuration reload rejected: {}", failure));

        Runtime.getRuntime().addShutdownHook(new Thread(registry::close));

        Javalin app = RackChatApi.create(registry, source, conversations);
        app.start(host(), port());
        log.info("RackChat is reading {} and knows {} connection(s): {}",
                config.toAbsolutePath(), registry.names().size(), registry.names());
    }

    private static Path configPath(String[] args) {
        if (args.length > 0 && !args[0].isBlank()) {
            return Path.of(args[0]);
        }
        String fromEnv = System.getenv("RACKCHAT_CONFIG");
        return Path.of(fromEnv == null || fromEnv.isBlank() ? DEFAULT_CONFIG : fromEnv);
    }

    private static int port() {
        String fromEnv = System.getenv("RACKCHAT_PORT");
        return fromEnv == null || fromEnv.isBlank() ? DEFAULT_PORT : Integer.parseInt(fromEnv);
    }

    /**
     * Loopback by default: the editor serves the configuration file's raw text, which is
     * whatever the file holds - including a literal key, if someone pasted one - and nothing
     * here authenticates. Binding elsewhere is a deliberate act, made with RACKCHAT_HOST.
     */
    private static String host() {
        String fromEnv = System.getenv("RACKCHAT_HOST");
        return fromEnv == null || fromEnv.isBlank() ? DEFAULT_HOST : fromEnv;
    }
}
