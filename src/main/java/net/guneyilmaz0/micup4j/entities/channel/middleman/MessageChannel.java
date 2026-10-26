package net.guneyilmaz0.micup4j.entities.channel.middleman;

import net.guneyilmaz0.micup4j.entities.*;
import net.guneyilmaz0.micup4j.entities.channel.ChannelType;
import net.guneyilmaz0.micup4j.requests.RestAction;

/** Supported source-compatible subset of a JDA message channel. */
public interface MessageChannel extends ISnowflake {
    String getAsMention();
    ChannelType getType();
    RestAction<Message> sendMessage(String content);
    RestAction<Message> sendMessageEmbeds(MessageEmbed... embeds);
    RestAction<Message> retrieveMessageById(String id);
    default RestAction<Message> retrieveMessageById(long id) { return retrieveMessageById(Long.toUnsignedString(id)); }
}
