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
        final java.util.List<net.guneyilmaz0.micup4j.utils.FileUpload> files = new java.util.ArrayList<>();
        State(Micup client, String method, String path, String guildId, ObjectNode body) {
            this.client = Objects.requireNonNull(client); this.method = method; this.path = path;
            this.guildId = guildId; this.body = new MessageBody(body);
        }
        CompletableFuture<Message> execute() {
            java.util.List<net.guneyilmaz0.micup4j.utils.FileUpload> uploads;
            synchronized (files) { uploads = java.util.List.copyOf(files); }
            ObjectNode payload = body.submit(() -> {
                if (method.equals("POST") && !body.hasVisibleContent() && uploads.isEmpty())
                    throw new IllegalArgumentException("A message requires content, embeds or files");
            });
            if (uploads.isEmpty())
                return client.getRestClient().request(method, path, payload).map(data -> Message.fromContext(client, data, guildId)).submit();
            var attachments = payload.putArray("attachments");
            for (int i = 0; i < uploads.size(); i++) {
                var file = uploads.get(i);
                var entry = attachments.addObject().put("id", i).put("filename", file.getName());
                if (file.getDescription() != null) entry.put("description", file.getDescription());
                if (file.isSpoiler()) entry.put("flags", 1 << 3);
            }
            return client.getRestClient().requestMultipart(method, path, payload, uploads)
                    .map(data -> Message.fromContext(client, data, guildId)).submit();
        }
    }
    /**
     * Replaces the message components; an empty call removes them.
     *
     * @param rows at most five action rows, message components only, unique custom ids
     * @return this action
     * @throws IllegalArgumentException if the rows are invalid for a message
     */
    public A setComponents(net.guneyilmaz0.micup4j.interactions.components.LayoutComponent... rows) { return setComponents(java.util.Arrays.asList(rows)); }

    /**
     * Replaces the message components; an empty collection removes them.
     *
     * @param rows at most five action rows, message components only, unique custom ids
     * @return this action
     * @throws IllegalArgumentException if the rows are invalid for a message
     */
    public A setComponents(java.util.Collection<? extends net.guneyilmaz0.micup4j.interactions.components.LayoutComponent> rows) { state.body.setComponents(rows); return self(); }

    /**
     * Appends action rows to the current components.
     *
     * @param rows the rows to add
     * @return this action
     * @throws IllegalArgumentException if the result would be invalid for a message
     */
    public A addComponents(net.guneyilmaz0.micup4j.interactions.components.LayoutComponent... rows) { state.body.addComponents(java.util.Arrays.asList(rows)); return self(); }

    /**
     * Replaces the components with one row.
     *
     * @param components the row's components
     * @return this action
     */
    public A setActionRow(net.guneyilmaz0.micup4j.interactions.components.ItemComponent... components) { return setComponents(net.guneyilmaz0.micup4j.interactions.components.ActionRow.of(components)); }

    /**
     * Appends one row.
     *
     * @param components the row's components
     * @return this action
     */
    public A addActionRow(net.guneyilmaz0.micup4j.interactions.components.ItemComponent... components) { return addComponents(net.guneyilmaz0.micup4j.interactions.components.ActionRow.of(components)); }

    /** Most files on one message; Micup's default {@code max_attachments_per_message}. */
    public static final int MAX_FILES = 10;

    /**
     * Replaces or extends the files. Only creation actions expose this.
     *
     * @param uploads the files
     * @param append  whether to keep the current files
     * @throws IllegalArgumentException if the result would exceed {@value #MAX_FILES} files
     * @throws IllegalStateException if the action was already submitted
     */
    protected final void files(Collection<? extends net.guneyilmaz0.micup4j.utils.FileUpload> uploads, boolean append) {
        Objects.requireNonNull(uploads, "files");
        synchronized (state.files) {
            if (state.body.isFrozen()) throw new IllegalStateException("Message action has already been submitted");
            var next = new java.util.ArrayList<net.guneyilmaz0.micup4j.utils.FileUpload>(append ? state.files : java.util.List.of());
            for (var upload : uploads) next.add(Objects.requireNonNull(upload, "file"));
            if (next.size() > MAX_FILES) throw new IllegalArgumentException("A message supports at most " + MAX_FILES + " files");
            state.files.clear(); state.files.addAll(next);
        }
    }
}
