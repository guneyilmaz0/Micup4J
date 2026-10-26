package net.guneyilmaz0.micup4j.interactions;

import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.entities.Guild;
import net.guneyilmaz0.micup4j.entities.ISnowflake;
import net.guneyilmaz0.micup4j.entities.Member;
import net.guneyilmaz0.micup4j.entities.MessageChannel;
import net.guneyilmaz0.micup4j.entities.User;

/**
 * A minimal, JDA-shaped view of an interaction, as returned by {@link InteractionHook#getInteraction()}.
 *
 * <p>Every interaction event implements this interface.
 */
public interface Interaction extends ISnowflake {
    /**
     * Returns the raw Micup interaction type ({@code 2} command, {@code 3} component, {@code 4} autocomplete,
     * {@code 5} modal submit).
     *
     * @return the raw interaction type
     */
    int getTypeRaw();

    /**
     * Returns whether the interaction happened in a guild.
     *
     * @return {@code true} for guild interactions
     */
    boolean isFromGuild();

    /**
     * Returns the guild the interaction happened in.
     *
     * @return the guild, or {@code null} outside a guild
     */
    Guild getGuild();

    /**
     * Returns the invoking user.
     *
     * @return the user
     */
    User getUser();

    /**
     * Returns the invoking member.
     *
     * @return the member, or {@code null} outside a guild or without member data
     */
    Member getMember();

    /**
     * Returns the channel the interaction happened in.
     *
     * @return a channel handle
     */
    MessageChannel getChannel();

    /**
     * Returns whether the initial callback has been submitted.
     *
     * @return {@code true} once acknowledged
     */
    boolean isAcknowledged();

    /**
     * Returns the session that received the interaction.
     *
     * @return the owning session
     */
    Micup getJDA();
}
