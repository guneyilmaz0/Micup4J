package net.guneyilmaz0.micup4j.events;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.requests.RestAction;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class InteractionCreateEvent extends GenericEvent implements net.guneyilmaz0.micup4j.entities.ISnowflake {
    private final String id, applicationId, token;
    private final int interactionType;
    private final long responseDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(2500);
    private final AtomicBoolean acknowledged = new AtomicBoolean();
    private final CompletableFuture<Void> acknowledgedResponse = new CompletableFuture<>();
    private final net.guneyilmaz0.micup4j.interactions.InteractionHook hook = new net.guneyilmaz0.micup4j.interactions.InteractionHook(this);

    public InteractionCreateEvent(Micup client, long sequence, JsonNode data) {
        super(client, "INTERACTION_CREATE", sequence, data);
        id = Json.id(data.path("id").asText());
        applicationId = Json.id(data.path("application_id").asText());
        token = data.path("token").asText();
        interactionType = data.path("type").asInt();
    }

    public int getInteractionType() {
        return interactionType;
    }

    public String getName() {
        return getData().path("data").path("name").asText("");
    }

    public String getCustomId() {
        return getData().path("data").path("custom_id").asText("");
    }

    @Override public String getId() { return id; }
    public boolean isAcknowledged() { return acknowledged.get(); }
    public net.guneyilmaz0.micup4j.interactions.InteractionHook getHook() { return hook; }
    /** Independent view: cancelling a hook action cannot cancel the initial acknowledgement. */
    public CompletableFuture<Void> acknowledgement() {
        return acknowledged.get() ? acknowledgedResponse.copy() : CompletableFuture.failedFuture(new IllegalStateException("Interaction has not been acknowledged"));
    }
    public String getGuildId() { return getData().path("guild_id").asText(null); }
    public boolean isFromGuild() { return getGuildId() != null; }
    public net.guneyilmaz0.micup4j.entities.Guild getGuild() {
        return isFromGuild() ? getMicup().getGuildReferenceById(getGuildId()) : null;
    }
    public net.guneyilmaz0.micup4j.entities.User getUser() {
        var data = getData(); var user = data.path("member").path("user");
        if (!user.isObject()) user = data.path("user");
        return net.guneyilmaz0.micup4j.entities.User.fromJson(user).withClient(getMicup());
    }
    public net.guneyilmaz0.micup4j.entities.Member getMember() {
        var member = getData().path("member");
        return isFromGuild() && member.isObject() ? net.guneyilmaz0.micup4j.entities.Member.fromJson(getGuildId(), member).withClient(getMicup()) : null;
    }
    public net.guneyilmaz0.micup4j.entities.MessageChannel getChannel() {
        return new net.guneyilmaz0.micup4j.entities.MessageChannel(getMicup(), getData().path("channel_id").asText(), getGuildId());
    }
    public java.util.List<net.guneyilmaz0.micup4j.interactions.commands.OptionMapping> getOptions() {
        var data = getData().path("data");
        var result = new java.util.ArrayList<net.guneyilmaz0.micup4j.interactions.commands.OptionMapping>();
        for (var option : selectedOptions())
            result.add(new net.guneyilmaz0.micup4j.interactions.commands.OptionMapping(getMicup(), getGuildId(), option, data.path("resolved")));
        return java.util.List.copyOf(result);
    }
    /** Scalar options on the selected subcommand, or the command itself. */
    protected JsonNode selectedOptions() {
        JsonNode options = getData().path("data").path("options");
        for (int depth = 0; depth < 2 && options.isArray() && options.size() == 1; depth++) {
            int type = options.get(0).path("type").asInt();
            if (type != 1 && type != 2) break;
            options = options.get(0).path("options");
        }
        return options;
    }
    public String getSubcommandGroup() {
        var first = getData().path("data").path("options").path(0);
        return first.path("type").asInt() == 2 ? first.path("name").asText() : null;
    }
    public String getSubcommandName() {
        var first = getData().path("data").path("options").path(0);
        if (first.path("type").asInt() == 2) first = first.path("options").path(0);
        return first.path("type").asInt() == 1 ? first.path("name").asText() : null;
    }
    public String getFullCommandName() {
        return getName() + (getSubcommandGroup() == null ? "" : " " + getSubcommandGroup())
                + (getSubcommandName() == null ? "" : " " + getSubcommandName());
    }
    public net.guneyilmaz0.micup4j.interactions.commands.OptionMapping getOption(String name) {
        java.util.Objects.requireNonNull(name);
        return getOptions().stream().filter(option -> option.getName().equals(name)).findFirst().orElse(null);
    }
    public <T> T getOption(String name, T fallback,
                          java.util.function.Function<net.guneyilmaz0.micup4j.interactions.commands.OptionMapping, T> resolver) {
        var option = getOption(name); return option == null ? fallback : resolver.apply(option);
    }
    public net.guneyilmaz0.micup4j.requests.restaction.interactions.ReplyCallbackAction reply(String content) { return reply(content, false); }
    public net.guneyilmaz0.micup4j.requests.restaction.interactions.ReplyCallbackAction reply(String content, boolean ephemeral) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Reply content is required");
        return new net.guneyilmaz0.micup4j.requests.restaction.interactions.ReplyCallbackAction(this, 4,
                Json.object().put("content", content).put("flags", ephemeral ? 64 : 0));
    }
    public net.guneyilmaz0.micup4j.requests.restaction.interactions.ReplyCallbackAction replyEmbeds(net.guneyilmaz0.micup4j.entities.MessageEmbed... embeds) {
        return replyEmbeds(false, embeds);
    }
    public net.guneyilmaz0.micup4j.requests.restaction.interactions.ReplyCallbackAction replyEmbeds(boolean ephemeral, net.guneyilmaz0.micup4j.entities.MessageEmbed... embeds) {
        var body = net.guneyilmaz0.micup4j.internal.MessagePayload.embeds(embeds).put("flags", ephemeral ? 64 : 0);
        return new net.guneyilmaz0.micup4j.requests.restaction.interactions.ReplyCallbackAction(this, 4, body);
    }

    /** Returns a copied raw option value; absent options are represented by an empty Optional. */
    public java.util.Optional<JsonNode> getOptionValue(String name) {
        java.util.Objects.requireNonNull(name);
        for (JsonNode option : selectedOptions())
            if (name.equals(option.path("name").asText()) && option.has("value")) return java.util.Optional.of(option.get("value").deepCopy());
        return java.util.Optional.empty();
    }

    public net.guneyilmaz0.micup4j.requests.restaction.interactions.ReplyCallbackAction deferReply() { return deferReply(false); }
    public net.guneyilmaz0.micup4j.requests.restaction.interactions.ReplyCallbackAction deferReply(boolean ephemeral) {
        return new net.guneyilmaz0.micup4j.requests.restaction.interactions.ReplyCallbackAction(this, 5, Json.object().put("flags", ephemeral ? 64 : 0));
    }

    /**
     * Supports component update, autocomplete and modal payloads in addition to basic replies.
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

    public RestAction<JsonNode> editOriginal(String content) {
        return editOriginal(Json.object().put("content", content));
    }

    /** Edits a deferred response with a message payload, including embeds or components. */
    public RestAction<JsonNode> editOriginal(JsonNode message) {
        return getMicup().getRestClient().request("PATCH", webhook() + "/messages/@original", message, false);
    }

    public RestAction<JsonNode> followup(String content, boolean ephemeral) {
        return getMicup().getRestClient().request("POST", webhook(), Json.object().put("content", content).put("flags", ephemeral ? 64 : 0), false);
    }

    public RestAction<JsonNode> editOriginalEmbeds(net.guneyilmaz0.micup4j.entities.MessageEmbed... embeds) {
        return editOriginal(net.guneyilmaz0.micup4j.internal.MessagePayload.embeds(embeds));
    }

    public RestAction<JsonNode> retrieveOriginal() {
        return getMicup().getRestClient().request("GET", webhook() + "/messages/@original", null, false);
    }

    public RestAction<Void> deleteOriginal() {
        return getMicup().getRestClient().request("DELETE", webhook() + "/messages/@original", null, false).map(value -> null);
    }

    private String webhook() {
        return "/webhooks/" + applicationId + "/" + Json.segment(token);
    }
}
