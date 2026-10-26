package net.guneyilmaz0.micup4j.examples;

import net.guneyilmaz0.micup4j.*;
import net.guneyilmaz0.micup4j.entities.Member;
import net.guneyilmaz0.micup4j.events.interaction.command.SlashCommandInteractionEvent;
import net.guneyilmaz0.micup4j.hooks.ListenerAdapter;
import net.guneyilmaz0.micup4j.interactions.commands.OptionType;
import net.guneyilmaz0.micup4j.interactions.commands.build.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Source-compiled moderation example: kick, ban and timeout with audit log reasons. */
public final class ModerationExample extends ListenerAdapter {
    public static void register(Micup client) {
        // Replaces the global command set. Include every definition that should remain installed.
        client.updateCommands().addCommands(Commands.slash("mod", "Moderate a member")
                .addSubcommands(
                        new SubcommandData("kick", "Kick a member").addOption(OptionType.USER, "member", "Member", true)
                                .addOption(OptionType.STRING, "reason", "Reason", false),
                        new SubcommandData("ban", "Ban a user").addOption(OptionType.USER, "member", "User", true)
                                .addOption(OptionType.STRING, "reason", "Reason", false),
                        new SubcommandData("timeout", "Time a member out").addOption(OptionType.USER, "member", "Member", true)
                                .addOptions(new OptionData(OptionType.INTEGER, "minutes", "Length in minutes", true).setRequiredRange(1, 40320))))
                .queue();
    }

    @Override public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("mod") || !event.isFromGuild()) return;
        Member target = event.getOption("member").getAsMember();
        if (target == null) { event.reply("That user is not a member of this server.").setEphemeral(true).queue(); return; }
        // Ban and timeout reasons accept any text; header reasons such as kick must be printable ASCII.
        String reason = event.getOption("reason", "No reason given", option -> option.getAsString());
        String moderator = event.getUser().getName();
        // The server checks permissions and role hierarchy; failures arrive in the error callback.
        switch (event.getSubcommandName()) {
            case "kick" -> target.kick().reason(ascii(moderator + ": " + reason)).queue(
                    ok -> event.reply("Kicked " + target.getEffectiveName()).setEphemeral(true).queue(),
                    error -> event.reply("Kick failed: " + error.getMessage()).setEphemeral(true).queue());
            case "ban" -> target.ban(1, TimeUnit.DAYS).reason(moderator + ": " + reason).queue(
                    ok -> event.reply("Banned " + target.getEffectiveName()).setEphemeral(true).queue(),
                    error -> event.reply("Ban failed: " + error.getMessage()).setEphemeral(true).queue());
            case "timeout" -> target.timeoutFor(Duration.ofMinutes(event.getOption("minutes").getAsLong())).reason(moderator).queue(
                    ok -> event.reply("Timed out " + target.getEffectiveName()).setEphemeral(true).queue(),
                    error -> event.reply("Timeout failed: " + error.getMessage()).setEphemeral(true).queue());
            default -> event.reply("Unknown action").setEphemeral(true).queue();
        }
    }

    private static String ascii(String text) { return text.replaceAll("[^\\x20-\\x7E]", "?"); }
}
