package net.guneyilmaz0.micup4j.examples;

import net.guneyilmaz0.micup4j.EmbedBuilder;
import net.guneyilmaz0.micup4j.entities.*;
import net.guneyilmaz0.micup4j.events.guild.GuildReadyEvent;
import net.guneyilmaz0.micup4j.hooks.ListenerAdapter;
import net.guneyilmaz0.micup4j.interactions.commands.OptionType;
import net.guneyilmaz0.micup4j.interactions.commands.build.Commands;

/** BMO's familiar guild setup and cached-mention avatar patterns, compiled by check. */
public final class BmoStyleAvatar extends ListenerAdapter {
    @Override public void onGuildReady(GuildReadyEvent event) {
        // Initialize per-guild services here. Register definitions separately when they change.
        System.out.println("Guild ready: " + event.getGuild().getName());
    }
    public static void register(Guild guild) {
        guild.upsertCommand(Commands.slash("avatar", "Show a user's avatar")
                .addOption(OptionType.USER, "user", "Choose a user", false)).queue();
    }
    public static void execute(Member member, Message message) {
        Member target = member;
        if (!message.getMentions().getMembers().isEmpty()) target = message.getMentions().getMembers().getFirst();
        String avatar = target.getUser().getAvatarUrl();
        if (avatar == null) { message.reply("This user has no custom avatar.").queue(); return; }
        message.getChannel().sendMessageEmbeds(new EmbedBuilder()
                .setAuthor(target.getUser().getName(), null, avatar)
                .setTitle("Link", avatar + "?size=1024")
                .setColor(target.getColors().getPrimary())
                .setImage(avatar + "?size=1024")
                .setFooter(member.getUser().getName() + " requested this", member.getUser().getAvatarUrl())
                .build()).queue();
    }
}
