package net.guneyilmaz0.micup4j;

import net.guneyilmaz0.micup4j.entities.EmbedBuilder;
import net.guneyilmaz0.micup4j.interactions.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BuildersTest {
    @Test void embedSnapshotsAreImmutableAndUnicodeLimitsUseCodePoints() {
        var builder = new EmbedBuilder().setTitle("😀".repeat(256)).setColor(0x3498DB).setDescription("First");
        var first = builder.build();
        builder.setDescription("Second");
        assertEquals("First", first.toJson().path("description").asText());
        var copy = first.toJson(); copy.put("description", "External mutation");
        assertEquals("First", first.toJson().path("description").asText());
        assertThrows(IllegalArgumentException.class, () -> builder.setTitle("😀".repeat(257)));
    }
    @Test void invalidEmbedUrlsAreRejectedWithoutEchoingCredentials() {
        var builder = new EmbedBuilder();
        var error = assertThrows(IllegalArgumentException.class, () -> builder.setImage("https://user:secret@example.com/image.png"));
        assertFalse(error.toString().contains("secret"));
        assertThrows(IllegalArgumentException.class, () -> builder.setThumbnail("javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> builder.setColor(-1));
        assertEquals("attachment://photo.png", builder.setImage("attachment://photo.png").build().toJson().path("image").path("url").asText());
    }
    @Test void embedFieldLimitIsEnforcedAndInvalidFieldsDoNotMutateBuilder() {
        var builder = new EmbedBuilder();
        assertThrows(IllegalArgumentException.class, () -> builder.addField("", "invalid", false));
        assertThrows(IllegalStateException.class, builder::build);
        for (int i = 0; i < 25; i++) builder.addField("Field " + i, "Value", i % 2 == 0);
        assertThrows(IllegalArgumentException.class, () -> builder.addField("26", "Value", false));
        assertEquals(25, builder.build().toJson().path("fields").size());
    }
    @Test void slashOptionsValidateOrderDuplicatesAndRemainImmutable() {
        var base = new SlashCommand("avatar", "Show a user's avatar");
        var command = base.addOption(OptionType.USER, "user", "Choose a user", false);
        assertTrue(base.options().isEmpty());
        assertEquals(6, command.toJson().path("options").get(0).path("type").asInt());
        assertThrows(IllegalArgumentException.class, () -> command.addOption(OptionType.STRING, "reason", "Reason", true));
        assertThrows(IllegalArgumentException.class, () -> command.addOption(OptionType.USER, "user", "Duplicate", false));
        assertThrows(UnsupportedOperationException.class, () -> command.options().clear());
        assertThrows(IllegalArgumentException.class, () -> new SlashCommand("UPPERCASE", "Invalid"));
        assertEquals("é", new SlashCommand("e\u0301", "NFC").name());
    }
}
