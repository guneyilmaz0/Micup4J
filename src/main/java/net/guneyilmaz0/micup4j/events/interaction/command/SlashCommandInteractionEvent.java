package net.guneyilmaz0.micup4j.events.interaction.command;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;

public final class SlashCommandInteractionEvent extends net.guneyilmaz0.micup4j.events.InteractionCreateEvent {
    public SlashCommandInteractionEvent(Micup client, long sequence, JsonNode data) { super(client, sequence, data); }
}
