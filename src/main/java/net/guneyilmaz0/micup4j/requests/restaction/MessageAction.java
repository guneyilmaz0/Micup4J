package net.guneyilmaz0.micup4j.requests.restaction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.entities.Message;
import net.guneyilmaz0.micup4j.entities.MessageEmbed;
import net.guneyilmaz0.micup4j.internal.MessageBody;
import net.guneyilmaz0.micup4j.requests.RestAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Shared lazy message payload builder. Mutation stops on the first execution that passes validation. */
public abstract class MessageAction<A extends MessageAction<A>> extends RestAction<Message> {
    private final State state;
    /**
     * Creates the action. Called by the concrete create and edit actions.
     *
     * @param client  the owning client
     * @param method  the HTTP method, {@code POST} for creates
     * @param path    the REST route
     * @param guildId the guild of the channel, or {@code null} for direct messages; used to bind the resulting {@link Message}
     * @param body    the initial payload, copied
     * @throws NullPointerException if {@code client} is {@code null}
     */
    protected MessageAction(Micup client, String method, String path, String guildId, ObjectNode body) {
        this(new State(client, method, path, guildId, body));
    }
    private MessageAction(State state) { super(state::execute); this.state = state; }
    /**
     * Returns this action as its concrete type for fluent chaining.
     *
     * @return this
     */
    protected abstract A self();
    /**
     * Replaces the content.
     *
     * @param content the new content; {@code null} clears it to an empty string
     * @return this action
     * @throws IllegalStateException if the action was already submitted
     */
    public A setContent(String content) { state.body.setContent(content); return self(); }
    /**
     * Replaces the embeds.
     *
     * @param embeds the embeds, at most 10
     * @return this action
     * @throws IllegalArgumentException if more than 10 embeds are given
     * @throws IllegalStateException if the action was already submitted
     */
    public A setEmbeds(MessageEmbed... embeds) { return setEmbeds(Arrays.asList(embeds)); }
    /**
     * Replaces the embeds.
     *
     * @param embeds the embeds, at most 10
     * @return this action
     * @throws IllegalArgumentException if more than 10 embeds are given
     * @throws IllegalStateException if the action was already submitted
     */
    public A setEmbeds(Collection<? extends MessageEmbed> embeds) { state.body.setEmbeds(embeds, false); return self(); }
    /**
     * Adds embeds to the current ones.
     *
     * @param embeds the embeds to add; the total must stay at most 10
     * @return this action
     * @throws IllegalArgumentException if the total would exceed 10 embeds
     * @throws IllegalStateException if the action was already submitted
     */
    public A addEmbeds(MessageEmbed... embeds) { return addEmbeds(Arrays.asList(embeds)); }
    /**
     * Adds embeds to the current ones.
     *
     * @param embeds the embeds to add; the total must stay at most 10
     * @return this action
     * @throws IllegalArgumentException if the total would exceed 10 embeds
     * @throws IllegalStateException if the action was already submitted
     */
    public A addEmbeds(Collection<? extends MessageEmbed> embeds) { state.body.setEmbeds(embeds, true); return self(); }
    private static final class State {
        final Micup client;
        final String method, path, guildId;
        final MessageBody body;
        State(Micup client, String method, String path, String guildId, ObjectNode body) {
            this.client = Objects.requireNonNull(client); this.method = method; this.path = path;
            this.guildId = guildId; this.body = new MessageBody(body);
        }
        CompletableFuture<Message> execute() {
            ObjectNode payload = body.submit(() -> {
                if (method.equals("POST") && !body.hasVisibleContent())
                    throw new IllegalArgumentException("A message requires content or embeds");
            });
            return client.getRestClient().request(method, path, payload)
                    .map(data -> Message.fromContext(client, data, guildId)).submit();
        }
    }
}
