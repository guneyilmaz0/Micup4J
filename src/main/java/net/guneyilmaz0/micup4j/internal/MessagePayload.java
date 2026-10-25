package net.guneyilmaz0.micup4j.internal;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.entities.MessageEmbed;
import java.util.Objects;

public final class MessagePayload {
    private MessagePayload() {}
    public static ObjectNode embeds(MessageEmbed... values) {
        if (values == null || values.length < 1 || values.length > 10) throw new IllegalArgumentException("Provide 1 to 10 embeds");
        var embeds = Json.MAPPER.createArrayNode();
        for (MessageEmbed value : values) embeds.add(Objects.requireNonNull(value).toJson());
        return Json.object().set("embeds", embeds);
    }
}
