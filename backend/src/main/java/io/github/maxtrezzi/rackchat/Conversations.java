package io.github.maxtrezzi.rackchat;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import io.github.maxtrezzi.modelrack4j.LlmBundle;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The chat histories the server is holding, one per conversation and connection.
 *
 * <p>The memory objects come from the bundle's own {@code ChatMemoryProvider}, so the
 * {@code memory} block in the configuration is what decides how much history a connection
 * sees — RackChat only decides <em>which</em> memory a request belongs to, and keeps it alive
 * between requests. A connection with no {@code memory} block has no memory here either:
 * that is the configuration's answer, not an oversight to work around.
 *
 * <p>The pair is deliberate. A memory carries its connection's eviction policy, so two
 * connections in one conversation keep separate histories: switching connection means the new
 * one starts fresh rather than inheriting a transcript shaped by another model's window.
 */
final class Conversations {

    /**
     * How many (conversation, connection) histories to keep. This is a personal tool with one
     * user, so the bound exists to stop an unbounded map rather than to size a workload; the
     * least recently used is dropped, and dropping one only loses that thread's history.
     */
    private static final int MAX_REMEMBERED = 200;

    record Key(String conversation, String connection) {
    }

    private final Map<Key, ChatMemory> memories = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, ChatMemory> eldest) {
            return size() > MAX_REMEMBERED;
        }
    };

    /**
     * The memory for this conversation on this connection, created once and reused, or empty
     * when the connection has no {@code memory} block configured.
     */
    synchronized Optional<ChatMemory> memoryFor(LlmBundle bundle, String conversation) {
        Optional<ChatMemoryProvider> provider = bundle.chatMemoryProvider();
        if (provider.isEmpty()) {
            return Optional.empty();
        }
        Key key = new Key(conversation, bundle.name());
        ChatMemory remembered = memories.get(key);
        if (remembered == null) {
            remembered = provider.get().get(key.conversation() + "/" + key.connection());
            memories.put(key, remembered);
        }
        return Optional.of(remembered);
    }

    /** Drops the histories of connections a reload removed; they can never be reached again. */
    synchronized void forget(Set<String> connections) {
        memories.keySet().removeIf(key -> connections.contains(key.connection()));
    }

    synchronized int size() {
        return memories.size();
    }
}
