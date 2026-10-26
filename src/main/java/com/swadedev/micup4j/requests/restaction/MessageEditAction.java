package com.swadedev.micup4j.requests.restaction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swadedev.micup4j.Micup;

/** Fluent lazy message editing with content and embed support. */
public final class MessageEditAction extends MessageAction<MessageEditAction> {
    /**
     * Creates the action; prefer {@code message.editMessage(...)}.
     *
     * @param client  the session
     * @param path    the message route
     * @param guildId the channel's guild, or {@code null} for direct messages
     * @param body    the initial changes, copied
     */
    public MessageEditAction(Micup client, String path, String guildId, ObjectNode body) { super(client, "PATCH", path, guildId, body); }
    @Override protected MessageEditAction self() { return this; }
}
