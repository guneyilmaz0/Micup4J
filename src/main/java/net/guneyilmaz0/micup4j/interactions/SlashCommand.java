package net.guneyilmaz0.micup4j.interactions;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.internal.Json;
/** Basic chat-input command. Advanced options can be registered through the REST client. */
public record SlashCommand(String name, String description) {
    public SlashCommand {
        if (name == null || !name.matches("[\\p{L}\\p{N}_-]{1,32}") || !name.equals(name.toLowerCase(java.util.Locale.ROOT)))
            throw new IllegalArgumentException("Invalid command name");
        if (description == null || description.isBlank() || description.length() > 100)
            throw new IllegalArgumentException("Description must contain 1 to 100 characters");
    }

    public ObjectNode toJson() {
        return Json.object().put("type", 1).put("name", name).put("description", description);
    }
}
