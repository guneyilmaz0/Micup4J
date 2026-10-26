package net.guneyilmaz0.micup4j.requests.restaction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.entities.*;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.requests.RestAction;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Shared lazy message payload builder. Mutation stops on the first execution. */
public abstract class MessageAction<A extends MessageAction<A>> extends RestAction<Message> {
    private final State state;
    protected MessageAction(Micup client, String method, String path, String guildId, ObjectNode body) {
        this(new State(client, method, path, guildId, body));
    }
    private MessageAction(State state) { super(state::execute); this.state = state; }
    protected abstract A self();
    public A setContent(String content) { state.content(content); return self(); }
    public A setEmbeds(MessageEmbed... embeds) { return setEmbeds(Arrays.asList(embeds)); }
    public A setEmbeds(Collection<? extends MessageEmbed> embeds) { state.embeds(embeds, false); return self(); }
    public A addEmbeds(MessageEmbed... embeds) { return addEmbeds(Arrays.asList(embeds)); }
    public A addEmbeds(Collection<? extends MessageEmbed> embeds) { state.embeds(embeds, true); return self(); }
    private static final class State {
        final Micup client;
        final String method, path, guildId;
        final ObjectNode body;
        boolean submitted;
        State(Micup client, String method, String path, String guildId, ObjectNode body) {
            this.client = Objects.requireNonNull(client); this.method = method; this.path = path;
            this.guildId = guildId; this.body = body.deepCopy();
        }
        void mutable() { if (submitted) throw new IllegalStateException("Message action has already been submitted"); }
        synchronized void content(String value) { mutable(); body.put("content", value == null ? "" : value); }
        synchronized void embeds(Collection<? extends MessageEmbed> values, boolean append) {
            mutable();
            var next = append && body.has("embeds") ? body.withArray("embeds").deepCopy() : Json.MAPPER.createArrayNode();
            for (var value : values) next.add(Objects.requireNonNull(value).toJson());
            if (next.size() > 10) throw new IllegalArgumentException("A message supports at most 10 embeds");
            body.set("embeds", next);
        }
        synchronized CompletableFuture<Message> execute() {
            if (method.equals("POST") && body.path("content").asText("").isBlank() && body.path("embeds").isEmpty())
                throw new IllegalArgumentException("A message requires content or embeds");
            submitted = true;
            return client.getRestClient().request(method, path, body.deepCopy())
                    .map(data -> Message.fromContext(client, data, guildId)).submit();
        }
    }
}
