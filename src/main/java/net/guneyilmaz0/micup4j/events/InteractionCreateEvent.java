package net.guneyilmaz0.micup4j.events;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.requests.RestAction;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class InteractionCreateEvent extends GenericEvent {
    private final String id, applicationId, token;
    private final int interactionType;
    private final long responseDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(2500);
    private final AtomicBoolean acknowledged = new AtomicBoolean();

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

    public RestAction<Void> reply(String content) {
        return reply(content, false);
    }

    public RestAction<Void> reply(String content, boolean ephemeral) {
        return respond(4, Json.object().put("content", content).put("flags", ephemeral ? 64 : 0));
    }

    public RestAction<Void> replyEmbeds(boolean ephemeral, net.guneyilmaz0.micup4j.entities.MessageEmbed... embeds) {
        var body = net.guneyilmaz0.micup4j.internal.MessagePayload.embeds(embeds).put("flags", ephemeral ? 64 : 0);
        return respond(4, body);
    }

    /** Returns a copied raw option value; absent options are represented by an empty Optional. */
    public java.util.Optional<JsonNode> getOptionValue(String name) {
        java.util.Objects.requireNonNull(name);
        for (JsonNode option : getData().path("data").path("options"))
            if (name.equals(option.path("name").asText()) && option.has("value")) return java.util.Optional.of(option.get("value").deepCopy());
        return java.util.Optional.empty();
    }

    public RestAction<Void> deferReply(boolean ephemeral) {
        return respond(5, Json.object().put("flags", ephemeral ? 64 : 0));
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
            return getMicup().getRestClient().requestCallback("/interactions/" + id + "/" + Json.segment(token) + "/callback", body, responseDeadline).submit();
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
