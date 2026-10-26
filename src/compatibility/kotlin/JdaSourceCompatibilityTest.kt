package net.guneyilmaz0.bmo

import java.awt.Color
import java.time.Duration
import java.util.concurrent.TimeUnit
import net.guneyilmaz0.micup4j.EmbedBuilder
import net.guneyilmaz0.micup4j.entities.Guild
import net.guneyilmaz0.micup4j.entities.Member
import net.guneyilmaz0.micup4j.entities.Message
import net.guneyilmaz0.micup4j.entities.RoleColors
import net.guneyilmaz0.micup4j.events.guild.GuildReadyEvent
import net.guneyilmaz0.micup4j.events.guild.member.GuildMemberJoinEvent
import net.guneyilmaz0.micup4j.events.guild.member.GuildMemberRemoveEvent
import net.guneyilmaz0.micup4j.events.message.MessageDeleteEvent
import net.guneyilmaz0.micup4j.events.message.MessageUpdateEvent
import net.guneyilmaz0.micup4j.events.session.ShutdownEvent
import net.guneyilmaz0.micup4j.hooks.ListenerAdapter
import net.guneyilmaz0.micup4j.interactions.commands.OptionType
import net.guneyilmaz0.micup4j.interactions.commands.build.Commands
import net.guneyilmaz0.micup4j.interactions.commands.build.OptionData
import net.guneyilmaz0.micup4j.interactions.commands.build.SubcommandData
import net.guneyilmaz0.micup4j.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.guneyilmaz0.micup4j.events.interaction.command.SlashCommandInteractionEvent
import net.guneyilmaz0.micup4j.interactions.InteractionHook
import net.guneyilmaz0.micup4j.requests.RestAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Compile the familiar BMO/JDA property access without executing live bot operations. */
class JdaSourceCompatibilityTest : ListenerAdapter() {
    @Test fun `role color properties expose Color values rather than raw record components`() {
        val colors = RoleColors(0x3498DB, 0)
        val primary: Color? = colors.primary
        val secondary: Color? = colors.secondary
        assertEquals(0x3498DB, primary!!.rgb and 0xFFFFFF)
        assertEquals(Color.BLACK, secondary)
        assertNull(RoleColors.DEFAULT.primary)
    }

    override fun onShutdown(event: ShutdownEvent) {
        check(event.code == 1000 && event.timeShutdown != null)
        check(event.jda.status == net.guneyilmaz0.micup4j.Micup.Status.SHUTDOWN)
    }

    override fun onGuildReady(event: GuildReadyEvent) {
        val guild: Guild = event.guild
        check(guild.id.isNotBlank())
    }

    override fun onMessageUpdate(event: MessageUpdateEvent) {
        if (event.isFromGuild && !event.author.isBot) {
            event.channel.sendMessage("${event.author.asMention} edited ${event.messageId}").queue()
        }
    }

    override fun onMessageDelete(event: MessageDeleteEvent) {
        val channelId: String = event.channelId
        check(event.messageIdLong != 0L && channelId.isNotBlank())
    }

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        event.guild.getTextChannelById(1L)?.sendMessage("Welcome ${event.member.asMention}!")?.queue()
    }

    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        val name: String = event.member?.effectiveName ?: event.user.id
        check(name.isNotEmpty() && event.guild.id.isNotBlank())
    }

    @Suppress("unused")
    private fun register(guild: Guild) {
        guild.upsertCommand(Commands.slash("avatar", "Show a user's avatar")
            .addOption(OptionType.USER, "user", "Choose a user", false)).queue()
    }

    override fun onCommandAutoCompleteInteraction(event: CommandAutoCompleteInteractionEvent) {
        if (event.fullCommandName == "help find" && event.focusedOption.name == "topic") {
            event.replyChoiceStrings("commands", "permissions").queue()
        }
    }

    @Suppress("unused")
    private fun commandToolkit(guild: Guild, message: Message) {
        guild.upsertCommand(Commands.slash("help", "Help topics")
            .addSubcommands(SubcommandData("find", "Find topic")
                .addOptions(OptionData(OptionType.STRING, "topic", "Topic", true, true)
                    .setMinLength(1).setMaxLength(100)))).queue()
        message.reply("Help").setEmbeds(EmbedBuilder().setTitle("Topics").build()).queue()
        message.editMessage("Updated").setEmbeds().queue()
    }

    @Suppress("unused")
    private fun avatar(member: Member, message: Message) {
        val target = message.mentions.members.firstOrNull() ?: member
        val avatar = target.user.avatarUrl ?: return
        message.channel.sendMessageEmbeds(EmbedBuilder().apply {
            setAuthor(target.user.name, null, avatar)
            setTitle("Link", "$avatar?size=1024")
            setColor(target.colors.primary)
            setImage("$avatar?size=1024")
            setFooter("${member.user.name} requested this", member.user.avatarUrl)
        }.build()).queue()
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        val embed = EmbedBuilder().setTitle("Working").build()
        event.deferReply(true).queue { hook ->
            hook.sendMessage("Done").setEphemeral(true).addEmbeds(embed).queue()
        }
    }

    @Suppress("unused")
    private fun hookToolkit(hook: InteractionHook, message: Message) {
        val embed = EmbedBuilder().setTitle("Status").build()
        hook.setEphemeral(true).sendMessageEmbeds(embed).addContent("Ready").queue()
        hook.editOriginal("Edited").setEmbeds(embed).queue()
        hook.editMessageById(message.id, "Edited followup").queue()
        hook.editMessageEmbedsById(message.idLong, embed).queue()
        hook.editMessageEmbedsById(message.idLong, listOf(embed)).queue()
        hook.retrieveMessageById(message.idLong).queue { found -> check(found.idLong == message.idLong) }
        hook.deleteMessageById(message.id).queueAfter(5, TimeUnit.SECONDS)
        check(hook.interaction.isFromGuild || hook.jda.guilds.isEmpty())
    }

    @Suppress("unused")
    private fun restActionToolkit(message: Message) {
        message.reply("Later").delay(Duration.ofSeconds(1)).queue()
        message.editMessage("Fallback").onErrorMap { message }.queue()
        message.channel.sendMessage("Scheduled").queueAfter(10, TimeUnit.SECONDS)
        RestAction.allOf(message.channel.sendMessage("a"), message.channel.sendMessage("b")).queue { sent ->
            check(sent.size == 2)
        }
        message.channel.sendMessage("x").zip(message.channel.sendMessage("y")).queue()
    }
}
