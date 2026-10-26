package net.guneyilmaz0.micup4j.events.message;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.internal.Json;

/**
 * Fired for a Micup {@code MESSAGE_DELETE} dispatch. Mirrors JDA's {@code MessageDeleteEvent}.
 *
 * <p>Micup may additionally send the author id and the content before deletion; both are omitted when an
 * administrator, moderation or the system deleted the message. Deleted content is private: never log it.
 * {@link #toString()} never includes it.
 */
public final class MessageDeleteEvent extends GenericMessageEvent {
    private final String authorId;
    private final String content;

    /**
     * Creates the event from a {@code MESSAGE_DELETE} payload.
     *
     * @param client   the owning client
     * @param sequence the Gateway sequence number
     * @param data     the payload with {@code id}, {@code channel_id} and optional {@code guild_id},
     *                 {@code author_id} and {@code content}
     * @throws IllegalArgumentException if an id is not a canonical snowflake
     */
    public MessageDeleteEvent(Micup client, long sequence, JsonNode data) {
        super(client, "MESSAGE_DELETE", sequence, data);
        JsonNode raw = rawData();
        authorId = raw.hasNonNull("author_id") ? Json.id(raw.path("author_id").asText(null)) : null;
        content = raw.path("content").isTextual() ? raw.path("content").asText() : null;
    }

    /**
     * Returns the id of the deleted message's author. Micup-only.
     *
     * @return the author id, or {@code null} when the server omitted it (moderation or system deletes)
     */
    public String getAuthorId() {
        return authorId;
    }

    /**
     * Returns the content of the message before deletion. Micup-only; JDA has no equivalent. The content is private
     * user data: never log it.
     *
     * @return the previous content, or {@code null} when the server omitted it
     */
    public String getContentRaw() {
        return content;
    }
}
