package io.github.maxtrezzi.rackchat;

import io.github.maxtrezzi.modelrack4j.LlmRegistry;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static final String DEFAULT_CONFIG = "rackchat.conf";
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

        LlmRegistry registry = LlmRegistry.builder()
                .configFiles(List.of(config))
                .watch(true)
                .build();

        registry.onReload(change -> log.info(
                "Configuration reloaded: {} added, {} updated, {} removed",
                change.added(), change.updated(), change.removed()));
        registry.onReloadFailure(failure -> log.warn("Configuration reload rejected: {}", failure));

        Runtime.getRuntime().addShutdownHook(new Thread(registry::close));

        Javalin app = RackChatApi.create(registry);
        app.start(port());
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
}
