package net.guneyilmaz0.micup4j.interactions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.internal.Json;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/** An immutable basic slash-command value option. */
public record CommandOption(OptionType type, String name, String description, boolean required) {
    public CommandOption { Objects.requireNonNull(type); name = validName(name); validDescription(description); }
    public ObjectNode toJson() {
        return Json.object().put("type", type.getKey()).put("name", name).put("description", description).put("required", required);
    }
    static String validName(String name) {
        Objects.requireNonNull(name);
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
        if (name.codePointCount(0, name.length()) > 32 || !name.matches("[\\p{L}\\p{N}_-]+") || !name.equals(name.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Command/option name must contain 1 to 32 lowercase letters, digits, underscores or hyphens");
        return name;
    }
    static void validDescription(String value) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > 100)
            throw new IllegalArgumentException("Description must contain 1 to 100 characters");
    }
}
