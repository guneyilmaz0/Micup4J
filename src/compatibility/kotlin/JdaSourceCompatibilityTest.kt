package net.guneyilmaz0.bmo

import java.awt.Color
import net.guneyilmaz0.micup4j.EmbedBuilder
import net.guneyilmaz0.micup4j.entities.Guild
import net.guneyilmaz0.micup4j.entities.Member
import net.guneyilmaz0.micup4j.entities.Message
import net.guneyilmaz0.micup4j.entities.RoleColors
import net.guneyilmaz0.micup4j.events.guild.GuildReadyEvent
import net.guneyilmaz0.micup4j.hooks.ListenerAdapter
import net.guneyilmaz0.micup4j.interactions.commands.OptionType
import net.guneyilmaz0.micup4j.interactions.commands.build.Commands
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

    override fun onGuildReady(event: GuildReadyEvent) {
        val guild: Guild = event.guild
        check(guild.id.isNotBlank())
    }

    @Suppress("unused")
    private fun register(guild: Guild) {
        guild.upsertCommand(Commands.slash("avatar", "Show a user's avatar")
            .addOption(OptionType.USER, "user", "Choose a user", false)).queue()
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
}
