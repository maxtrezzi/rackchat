package io.github.maxtrezzi.rackchat;

import io.javalin.Javalin;

public final class Main {

    private Main() {
    }

    public static Javalin createApp() {
        return Javalin.create(config -> config.routes.get("/health", ctx -> ctx.result("ok")));
    }

    public static void main(String[] args) {
        createApp().start(7070);
    }
}
