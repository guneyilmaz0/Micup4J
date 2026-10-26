package net.guneyilmaz0.micup4j.requests.restaction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.Micup;

/** Fluent lazy message editing with content and embed support. */
public final class MessageEditAction extends MessageAction<MessageEditAction> {
    public MessageEditAction(Micup client, String path, String guildId, ObjectNode body) { super(client, "PATCH", path, guildId, body); }
    @Override protected MessageEditAction self() { return this; }
}
