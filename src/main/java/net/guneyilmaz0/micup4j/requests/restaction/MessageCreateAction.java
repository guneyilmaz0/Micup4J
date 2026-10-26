package net.guneyilmaz0.micup4j.requests.restaction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.Micup;

/** Fluent lazy message creation with content and embed support. */
public final class MessageCreateAction extends MessageAction<MessageCreateAction> {
    public MessageCreateAction(Micup client, String path, String guildId, ObjectNode body) { super(client, "POST", path, guildId, body); }
    @Override protected MessageCreateAction self() { return this; }
}
