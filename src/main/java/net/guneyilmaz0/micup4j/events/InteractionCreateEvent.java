package net.guneyilmaz0.micup4j.events;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.requests.RestAction;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fired for a Micup {@code INTERACTION_CREATE} dispatch. Mirrors JDA's {@code GenericInteractionCreateEvent} and, for
 * commands, {@code SlashCommandInteractionEvent}.
 *
 * <p>The initial response must be sent within 2.5 seconds of the event being created; at most one initial response is
 * accepted ({@link #isAcknowledged()}). After it, {@link #getHook()} sends followups and edits. The payload contains the
 * interaction token: {@link #toString()} never includes it, and it must never be logged.
 *
 * <p>Option accessors are parsed lazily and the results are cached; the event is safe to read from any thread.
 */
public class InteractionCreateEvent extends GenericEvent implements net.guneyilmaz0.micup4j.interactions.Interaction {
    private final String id, applicationId, token;
    private final int interactionType;
    private final String name, customId, guildId, channelId, subcommandGroup, subcommandName;
    /** Read-only references into {@link #rawData()}; never mutated after construction. */
    private final JsonNode selected, resolved;
    // Racy single-check caches: construction is pure and the results are immutable, so a duplicate build is harmless.
    private volatile java.util.List<net.guneyilmaz0.micup4j.interactions.commands.OptionMapping> options;
    private volatile net.guneyilmaz0.micup4j.entities.User user;
    private volatile net.guneyilmaz0.micup4j.entities.Member member;
    private final long responseDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(2500);
    private final AtomicBoolean acknowledged = new AtomicBoolean();
    private final CompletableFuture<Void> acknowledgedResponse = new CompletableFuture<>();
    private final net.guneyilmaz0.micup4j.interactions.InteractionHook hook = new net.guneyilmaz0.micup4j.interactions.InteractionHook(this);

    /**
     * Parses the interaction payload once. Scalar fields are read eagerly; options, user and member are built lazily.
     *
     * @param client   the owning session
     * @param sequence the Gateway sequence number of the dispatch
     * @param data     the {@code INTERACTION_CREATE} payload; copied, never retained
     * @throws IllegalArgumentException if the id, application id or guild id is not a canonical snowflake, or the
     *                                  token is missing, not a string or blank (the token value is never included in
     *                                  the message)
     */
    public InteractionCreateEvent(Micup client, long sequence, JsonNode data) {
        super(client, "INTERACTION_CREATE", sequence, data);
        JsonNode raw = rawData();
        id = Json.id(raw.path("id").asText());
        applicationId = Json.id(raw.path("application_id").asText());
        JsonNode tokenNode = raw.path("token");
        if (!tokenNode.isTextual() || tokenNode.textValue().isBlank())
            throw new IllegalArgumentException("Interaction payload has no token");
        token = tokenNode.textValue();
        interactionType = raw.path("type").asInt();
        JsonNode body = raw.path("data");
        name = body.path("name").asText("");
        customId = body.path("custom_id").asText("");
        guildId = raw.hasNonNull("guild_id") ? Json.id(raw.path("guild_id").asText()) : null;
        channelId = raw.path("channel_id").asText();
        JsonNode first = body.path("options").path(0);
        subcommandGroup = first.path("type").asInt() == 2 ? first.path("name").asText() : null;
        JsonNode sub = first.path("type").asInt() == 2 ? first.path("options").path(0) : first;
        subcommandName = sub.path("type").asInt() == 1 ? sub.path("name").asText() : null;
        selected = selectOptions(body.path("options"));
        resolved = body.path("resolved");
    }

    private static JsonNode selectOptions(JsonNode options) {
        for (int depth = 0; depth < 2 && options.isArray() && options.size() == 1; depth++) {
            int type = options.get(0).path("type").asInt();
            if (type != 1 && type != 2) break;
            options = options.get(0).path("options");
        }
        return options;
    }

    /**
     * Returns the raw Micup interaction type.
     *
     * @return the raw type, see {@link #getTypeRaw()}
     */
    public int getInteractionType() {
        return interactionType;
    }

    /**
     * Returns the raw Micup interaction type; an alias of {@link #getInteractionType()}.
     *
     * @return the raw interaction type
     */
    @Override public int getTypeRaw() {
        return interactionType;
    }

    /**
     * Returns the invoked command name, or an empty string for non-command interactions.
     *
     * @return the invoked command name, or an empty string for non-command interactions
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the component or modal custom id, or an empty string for interactions without one.
     *
     * @return the component or modal custom id, or an empty string for interactions without one
     */
    public String getCustomId() {
        return customId;
    }

    /** Returns the interaction id. */
    @Override public String getId() { return id; }
    /**
     * Returns whether the initial response was submitted. The flag is set when the callback is executed, before the request completes, and stays set if the request fails.
     *
     * @return {@code true} once an initial response was attempted
     */
    public boolean isAcknowledged() { return acknowledged.get(); }
    /**
     * Returns the hook for followups and edits. It waits for the initial acknowledgement before it sends anything.
     *
     * @return the hook; the same instance on every call
     */
    public net.guneyilmaz0.micup4j.interactions.InteractionHook getHook() { return hook; }
    /**
     * Returns an independent view of the initial acknowledgement; cancelling it cannot cancel the acknowledgement.
     *
     * @return a future completing when the initial response succeeded
     */
    public CompletableFuture<Void> acknowledgement() {
        return acknowledged.get() ? acknowledgedResponse.copy() : CompletableFuture.failedFuture(new IllegalStateException("Interaction has not been acknowledged"));
    }
    /**
     * Returns the guild id, or {@code null} when the interaction did not happen in a guild.
     *
     * @return the guild id, or {@code null} when the interaction did not happen in a guild
     */
    public String getGuildId() { return guildId; }
    /**
     * Returns whether the interaction happened in a guild.
     *
     * @return {@code true} for guild interactions
     */
    public boolean isFromGuild() { return guildId != null; }
    /**
     * Returns the guild: the cached snapshot, or an id-only reference when it is not cached.
     *
     * @return the guild, or {@code null} outside a guild
     */
    public net.guneyilmaz0.micup4j.entities.Guild getGuild() {
        return isFromGuild() ? getMicup().getGuildReferenceById(guildId) : null;
    }
    /** Returns the invoking user, taken from the guild member when present. Built once and cached. */
    public net.guneyilmaz0.micup4j.entities.User getUser() {
        var result = user;
        if (result == null) {
            var node = rawData().path("member").path("user");
            if (!node.isObject()) node = rawData().path("user");
            user = result = net.guneyilmaz0.micup4j.entities.User.fromJson(node).withClient(getMicup());
        }
        return result;
    }
    /** Returns the invoking member, or {@code null} outside a guild or without member data. Built once and cached. */
    public net.guneyilmaz0.micup4j.entities.Member getMember() {
        var result = member;
        if (result == null) {
            var node = rawData().path("member");
            if (!isFromGuild() || !node.isObject()) return null;
            member = result = net.guneyilmaz0.micup4j.entities.Member.fromJson(guildId, node).withClient(getMicup());
        }
        return result;
    }
    /**
     * Returns a lightweight handle to the channel of the interaction. Creating it performs no request.
     *
     * @return the channel
     */
    public net.guneyilmaz0.micup4j.entities.MessageChannel getChannel() {
        return new net.guneyilmaz0.micup4j.entities.MessageChannel(getMicup(), channelId, guildId);
    }
    /**
     * Returns the scalar options of the selected subcommand, or of the command itself. The list is built on first use and
     * the same immutable instance is returned afterwards.
     *
     * @return the options
     * @throws IllegalArgumentException if any selected option has no scalar value
     */
    public java.util.List<net.guneyilmaz0.micup4j.interactions.commands.OptionMapping> getOptions() {
        var result = options;
        if (result == null) {
            var list = new java.util.ArrayList<net.guneyilmaz0.micup4j.interactions.commands.OptionMapping>();
            for (var option : selected)
                list.add(new net.guneyilmaz0.micup4j.interactions.commands.OptionMapping(getMicup(), guildId, option, resolved));
            options = result = java.util.List.copyOf(list);
        }
        return result;
    }
    /**
     * Returns the scalar options of the selected subcommand, or the command itself, without copying. Callers must treat
     * the node as read-only and must not log it.
     *
     * @return the internal options node
     */
    protected JsonNode selectedOptions() {
        return selected;
    }
    /**
     * Returns the selected subcommand group.
     *
     * @return the group name, or {@code null} if none
     */
    public String getSubcommandGroup() {
        return subcommandGroup;
    }
    /**
     * Returns the selected subcommand.
     *
     * @return the subcommand name, or {@code null} if none
     */
    public String getSubcommandName() {
        return subcommandName;
    }
    /**
     * Returns the command name followed by the subcommand group and subcommand, separated by spaces.
     *
     * @return the full command name
     */
    public String getFullCommandName() {
        return name + (subcommandGroup == null ? "" : " " + subcommandGroup)
                + (subcommandName == null ? "" : " " + subcommandName);
    }
    /**
     * Returns the selected option with the given name. Reuses the cached {@link #getOptions()} list when it was already
     * built; otherwise only the matching option is mapped, so a malformed unrelated option does not cause a failure.
     *
     * @param name the option name
     * @return the option, or {@code null} when absent
     */
    public net.guneyilmaz0.micup4j.interactions.commands.OptionMapping getOption(String name) {
        java.util.Objects.requireNonNull(name);
        var cached = options;
        if (cached != null) {
            for (var option : cached) if (option.getName().equals(name)) return option;
            return null;
        }
        for (var option : selected)
            if (name.equals(option.path("name").asText()))
                return new net.guneyilmaz0.micup4j.interactions.commands.OptionMapping(getMicup(), guildId, option, resolved);
        return null;
    }
    /**
     * Maps an option, or returns a fallback when the option is absent.
     *
     * @param <T>      the result type
     * @param name     the option name
     * @param fallback the value returned when the option is absent
     * @param resolver maps the present option to the result
     * @return the mapped value, or {@code fallback}
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public <T> T getOption(String name, T fallback,
                          java.util.function.Function<net.guneyilmaz0.micup4j.interactions.commands.OptionMapping, T> resolver) {
        var option = getOption(name); return option == null ? fallback : resolver.apply(option);
    }
    /**
     * Replies with a public message.
     *
     * @param content the message content, not blank
     * @return an action that sends the reply when queued, completing with the acknowledged hook
     * @throws IllegalArgumentException if {@code content} is {@code null} or blank
     */
    public net.guneyilmaz0.micup4j.requests.restaction.interactions.ReplyCallbackAction reply(String content) { return reply(content, false); }
    /**
     * Replies with a message.
     *
     * @param content   the message content, not blank
     * @param ephemeral {@code true} to make the reply visible only to the invoking user
     * @return an action that sends the reply when queued, completing with the acknowledged hook
     * @throws IllegalArgumentException if {@code content} is {@code null} or blank
     */
    public net.guneyilmaz0.micup4j.requests.restaction.interactions.ReplyCallbackAction reply(String content, boolean ephemeral) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Reply content is required");
        return new net.guneyilmaz0.micup4j.requests.restaction.interactions.ReplyCallbackAction(this, 4,
                Json.object().put("content", content).put("flags", ephemeral ? 64 : 0));
    }
    /**
     * Replies with a public message that contains embeds.
     *
     * @param embeds the embeds, at most 10
     * @return an action that sends the reply when queued, completing with the acknowledged hook
     * @throws IllegalArgumentException if the embeds are invalid
     */
    public net.guneyilmaz0.micup4j.requests.restaction.interactions.ReplyCallbackAction replyEmbeds(net.guneyilmaz0.micup4j.entities.MessageEmbed... embeds) {
        return replyEmbeds(false, embeds);
    }
    /**
     * Replies with a message that contains embeds.
     *
     * @param ephemeral {@code true} to make the reply visible only to the invoking user
     * @param embeds    the embeds, at most 10
     * @return an action that sends the reply when queued, completing with the acknowledged hook
     * @throws IllegalArgumentException if the embeds are invalid
     */
    public net.guneyilmaz0.micup4j.requests.restaction.interactions.ReplyCallbackAction replyEmbeds(boolean ephemeral, net.guneyilmaz0.micup4j.entities.MessageEmbed... embeds) {
        var body = net.guneyilmaz0.micup4j.internal.MessagePayload.embeds(embeds).put("flags", ephemeral ? 64 : 0);
        return new net.guneyilmaz0.micup4j.requests.restaction.interactions.ReplyCallbackAction(this, 4, body);
    }

    /**
     * Returns a copied raw option value.
     *
     * @param name the option name
     * @return the value, or empty when the option is absent
     */
    public java.util.Optional<JsonNode> getOptionValue(String name) {
        java.util.Objects.requireNonNull(name);
        for (JsonNode option : selected)
            if (name.equals(option.path("name").asText()) && option.has("value")) return java.util.Optional.of(option.get("value").deepCopy());
        return java.util.Optional.empty();
    }

    /**
     * Acknowledges the interaction and defers the reply, as with JDA's {@code deferReply()}. The visibility of the eventual
     * reply is public.
     *
     * @return an action that sends the deferral when queued, completing with the acknowledged hook
     */
    public net.guneyilmaz0.micup4j.requests.restaction.interactions.ReplyCallbackAction deferReply() { return deferReply(false); }
    /**
     * Acknowledges the interaction and defers the reply.
     *
     * @param ephemeral {@code true} to make the eventual reply visible only to the invoking user; this cannot be changed
     *                  later, so {@code setEphemeral} on the first send is ignored
     * @return an action that sends the deferral when queued, completing with the acknowledged hook
     */
    public net.guneyilmaz0.micup4j.requests.restaction.interactions.ReplyCallbackAction deferReply(boolean ephemeral) {
        return new net.guneyilmaz0.micup4j.requests.restaction.interactions.ReplyCallbackAction(this, 5, Json.object().put("flags", ephemeral ? 64 : 0));
    }

    /**
     * Submits the initial callback. Supports component update, autocomplete and modal payloads in addition to basic
     * replies. Micup-specific; prefer {@code reply*}, {@code deferReply*} or {@code replyChoices*}.
     *
     * <p>Only one initial response is accepted. The guard is set when the action is executed and is kept even if the
     * transport fails, because the server may already have consumed the token. A callback that cannot be sent before
     * the 2.5 second response deadline fails with a {@link java.util.concurrent.TimeoutException}.
     *
     * @param callbackType the Micup callback type, which must be valid for this interaction type
     * @param data         the callback data, or {@code null}; copied
     * @return an action that submits the callback when queued; it fails with an {@link IllegalStateException} when the
     *         interaction was already answered
     * @throws IllegalArgumentException if the callback type is not valid for this interaction type
     */
    public RestAction<Void> respond(int callbackType, JsonNode data) {
        boolean valid = switch (interactionType) {
            case 2 -> callbackType == 4 || callbackType == 5 || callbackType == 9;
            case 3 ->
                    callbackType == 4 || callbackType == 5 || callbackType == 6 || callbackType == 7 || callbackType == 9;
            case 4 -> callbackType == 8;
            case 5 -> callbackType == 4 || callbackType == 5;
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("Callback type is invalid for this interaction");
        var body = Json.object().put("type", callbackType);
        if (data != null) body.set("data", data.deepCopy());
        return new RestAction<JsonNode>(() -> {
            // The guard is claimed when the action runs, not when it is built, so an unexecuted action never consumes the response.
            if (!acknowledged.compareAndSet(false, true))
                return CompletableFuture.failedFuture(new IllegalStateException("Interaction response already submitted"));
            // Keep the guard on transport failure: the server may already have consumed the token.
            hook.markCallback(callbackType, (body.path("data").path("flags").asInt() & 64) != 0);
            var response = getMicup().getRestClient().requestCallback("/interactions/" + id + "/" + Json.segment(token) + "/callback", body, responseDeadline).submit();
            var result = new CompletableFuture<JsonNode>();
            result.whenComplete((value, error) -> {
                if (result.isCancelled()) {
                    acknowledgedResponse.completeExceptionally(new IllegalStateException("Interaction acknowledgement cancelled"));
                    response.cancel(true);
                }
            });
            response.whenComplete((value, error) -> {
                // Settle the shared barrier before exposing the hook to callback consumers.
                if (error == null) acknowledgedResponse.complete(null);
                else acknowledgedResponse.completeExceptionally(new IllegalStateException("Interaction acknowledgement failed"));
                if (response.isCancelled()) result.cancel(false);
                else if (error != null) result.completeExceptionally(error);
                else result.complete(value);
            });
            return result;
        }).map(value -> null);
    }

    /**
     * Edits the original response with new content. Does not wait for the acknowledgement; prefer
     * {@code getHook().editOriginal(...)}.
     *
     * @param content the new content
     * @return the raw edited message
     */
    public RestAction<JsonNode> editOriginal(String content) {
        return editOriginal(Json.object().put("content", content));
    }

    /**
     * Edits the original response with a message payload, including embeds or components. Does not wait for the
     * acknowledgement; prefer {@code getHook().editOriginal(...)}.
     *
     * @param message the message payload
     * @return the raw edited message
     */
    public RestAction<JsonNode> editOriginal(JsonNode message) {
        return getMicup().getRestClient().request("PATCH", webhook() + "/messages/@original", message, false);
    }

    /**
     * Sends a followup message. Does not wait for the acknowledgement; prefer {@code getHook().sendMessage(...)}.
     *
     * @param content   the message content
     * @param ephemeral {@code true} to make the message visible only to the invoking user
     * @return the raw created message
     */
    public RestAction<JsonNode> followup(String content, boolean ephemeral) {
        return getMicup().getRestClient().request("POST", webhook(), Json.object().put("content", content).put("flags", ephemeral ? 64 : 0), false);
    }

    /**
     * Sends a followup message with a raw payload. Micup-specific; prefer {@code getHook().sendMessage(...)}.
     * Does not wait for the acknowledgement.
     *
     * @param message the message payload, including {@code flags}
     * @return the raw created message
     */
    public RestAction<JsonNode> followup(JsonNode message) {
        return getMicup().getRestClient().request("POST", webhook(), java.util.Objects.requireNonNull(message), false);
    }

    /**
     * Edits a message of this interaction with a raw payload. Micup-specific; prefer
     * {@code getHook().editMessageById(...)}. Does not wait for the acknowledgement.
     *
     * @param messageId a canonical message id, or {@code "@original"}
     * @param message the fields to replace
     * @return the raw edited message
     * @throws IllegalArgumentException if {@code messageId} is not a canonical snowflake or {@code "@original"}
     */
    public RestAction<JsonNode> editFollowup(String messageId, JsonNode message) {
        return getMicup().getRestClient().request("PATCH", messagePath(messageId), java.util.Objects.requireNonNull(message), false);
    }

    /**
     * Retrieves a message of this interaction. Micup-specific; prefer {@code getHook().retrieveMessageById(...)}.
     *
     * @param messageId a canonical message id, or {@code "@original"}
     * @return the raw message
     * @throws IllegalArgumentException if {@code messageId} is not a canonical snowflake or {@code "@original"}
     */
    public RestAction<JsonNode> retrieveFollowup(String messageId) {
        return getMicup().getRestClient().request("GET", messagePath(messageId), null, false);
    }

    /**
     * Deletes a message of this interaction. Micup-specific; prefer {@code getHook().deleteMessageById(...)}.
     *
     * @param messageId a canonical message id, or {@code "@original"}
     * @return the delete action
     * @throws IllegalArgumentException if {@code messageId} is not a canonical snowflake or {@code "@original"}
     */
    public RestAction<Void> deleteFollowup(String messageId) {
        return getMicup().getRestClient().request("DELETE", messagePath(messageId), null, false).map(value -> null);
    }

    private String messagePath(String messageId) {
        java.util.Objects.requireNonNull(messageId, "messageId");
        return webhook() + "/messages/" + ("@original".equals(messageId) ? messageId : Json.id(messageId));
    }

    /**
     * Replaces the embeds of the original response. Does not wait for the acknowledgement; prefer
     * {@code getHook().editOriginalEmbeds(...)}.
     *
     * @param embeds the embeds, at most 10
     * @return the raw edited message
     * @throws IllegalArgumentException if the embeds are invalid
     */
    public RestAction<JsonNode> editOriginalEmbeds(net.guneyilmaz0.micup4j.entities.MessageEmbed... embeds) {
        return editOriginal(net.guneyilmaz0.micup4j.internal.MessagePayload.embeds(embeds));
    }

    /**
     * Retrieves the original response. Does not wait for the acknowledgement; prefer {@code getHook().retrieveOriginal()}.
     *
     * @return the raw message
     */
    public RestAction<JsonNode> retrieveOriginal() {
        return getMicup().getRestClient().request("GET", webhook() + "/messages/@original", null, false);
    }

    /**
     * Deletes the original response. Does not wait for the acknowledgement; prefer {@code getHook().deleteOriginal()}.
     *
     * @return the delete action
     */
    public RestAction<Void> deleteOriginal() {
        return getMicup().getRestClient().request("DELETE", webhook() + "/messages/@original", null, false).map(value -> null);
    }

    private String webhook() {
        return "/webhooks/" + applicationId + "/" + Json.segment(token);
    }
}
