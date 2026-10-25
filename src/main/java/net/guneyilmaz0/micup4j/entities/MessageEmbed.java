package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** Immutable embed snapshot built by EmbedBuilder. */
public final class MessageEmbed {
    private final ObjectNode data;
    MessageEmbed(ObjectNode data) { this.data = data.deepCopy(); }
    public ObjectNode toJson() { return data.deepCopy(); }
}
