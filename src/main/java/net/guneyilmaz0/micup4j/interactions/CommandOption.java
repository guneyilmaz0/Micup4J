package net.guneyilmaz0.micup4j.interactions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.internal.Json;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/**
 * A legacy immutable value option of {@link SlashCommand}.
 *
 * @param type        the option type
 * @param name        1-32 lowercase characters
 * @param description 1-100 characters
 * @param required    whether the user must fill it
 * @deprecated Use {@link net.guneyilmaz0.micup4j.interactions.commands.build.OptionData}; kept through 1.x.
 */
@Deprecated(since = "1.0.0")
public record CommandOption(OptionType type, String name, String description, boolean required) {
    /**
     * Validates the option.
     *
     * @throws IllegalArgumentException if a value is invalid
     */
    public CommandOption { Objects.requireNonNull(type); name = validName(name); validDescription(description); }
    /**
     * Returns the option payload.
     *
     * @return the option payload
     */
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
