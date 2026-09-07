package io.github.maxtrezzi.rackchat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import io.github.maxtrezzi.modelrack4j.LlmBundle;

import java.util.LinkedHashMap;
import java.util.List;
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
 * <p>The pair is deliberate: a memory carries its connection's eviction policy and accumulates
 * incrementally, so nothing is re-estimated as a conversation grows.
 *
 * <p><strong>A conversation reaching a connection for the first time is seeded</strong> with
 * what it already has (ADR-0013). The source is the memory the conversation last completed an
 * exchange on, and the messages are replayed <em>through the new memory's own {@code add}</em>,
 * so the receiving connection's window still decides what it sees. Copying the list instead
 * would put messages in front of a model that its {@code memory} block excludes.
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

    private final Map<Key, ChatMemory> memories = leastRecentlyUsed();

    /**
     * Which connection each conversation last completed an exchange on: the memory a switch
     * carries from. It follows completed exchanges rather than requests, so a call that failed
     * on another connection does not become the source of a history it never received.
     */
    private final Map<String, String> answeredLast = leastRecentlyUsed();

    /**
     * The memory for this conversation on this connection, created once and reused, or empty
     * when the connection has no {@code memory} block configured. A memory created here starts
     * with the conversation's history behind it, as far as this connection's window keeps it.
     */
    synchronized Optional<ChatMemory> memoryFor(LlmBundle<?> bundle, String conversation) {
        Optional<ChatMemoryProvider> provider = bundle.chatMemoryProvider();
        if (provider.isEmpty()) {
            return Optional.empty();
        }
        Key key = new Key(conversation, bundle.name());
        ChatMemory remembered = memories.get(key);
        if (remembered == null) {
            remembered = provider.get().get(key.conversation() + "/" + key.connection());
            carryInto(remembered, conversation);
            memories.put(key, remembered);
        }
        return Optional.of(remembered);
    }

    /**
     * Records a completed exchange: the question and the answer it came back with, and the
     * connection the conversation is now on. Both halves land together, so a failed call
     * leaves neither a dangling question nor a source pointing at a history that has none.
     *
     * <p>The connection is recorded even when it has no memory of its own — the conversation
     * did move there, and what it kept is nothing.
     */
    synchronized void remember(LlmBundle<?> bundle, String conversation, UserMessage question, AiMessage answer) {
        memoryFor(bundle, conversation).ifPresent(remembered -> {
            synchronized (remembered) {
                remembered.add(question);
                remembered.add(answer);
            }
        });
        answeredLast.put(conversation, bundle.name());
    }

    /** Drops the histories of connections a reload removed; they can never be reached again. */
    synchronized void forget(Set<String> connections) {
        memories.keySet().removeIf(key -> connections.contains(key.connection()));
        answeredLast.values().removeIf(connections::contains);
    }

    synchronized int size() {
        return memories.size();
    }

    /**
     * Replays the conversation's history into a memory that has just been created. Nothing is
     * carried when the conversation is new, when the connection it last answered on kept no
     * history, or when that history has since been dropped.
     */
    private void carryInto(ChatMemory fresh, String conversation) {
        String source = answeredLast.get(conversation);
        if (source == null) {
            return;
        }
        ChatMemory carried = memories.get(new Key(conversation, source));
        if (carried == null) {
            return;
        }
        List<ChatMessage> messages;
        synchronized (carried) {
            messages = List.copyOf(carried.messages());
        }
        messages.forEach(fresh::add);
    }

    private static <K, V> Map<K, V> leastRecentlyUsed() {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > MAX_REMEMBERED;
            }
        };
    }
}
