package net.guneyilmaz0.micup4j;

import net.guneyilmaz0.micup4j.entities.MessageEmbed;
import java.time.Instant;

/** JDA-style import location; validation follows Micup's documented embed limits. */
public class EmbedBuilder extends net.guneyilmaz0.micup4j.entities.EmbedBuilder {
    /** Creates an empty builder. */
    public EmbedBuilder() {}
    /**
     * Creates a builder copying a built embed.
     *
     * @param embed the embed to copy
     */
    public EmbedBuilder(MessageEmbed embed) { super(embed); }
    /**
     * Creates a builder copying another builder.
     *
     * @param builder the builder to copy
     */
    public EmbedBuilder(EmbedBuilder builder) { super(builder); }
    @Override public EmbedBuilder setTitle(String title) { super.setTitle(title); return this; }
    @Override public EmbedBuilder setTitle(String title, String url) { super.setTitle(title, url); return this; }
    @Override public EmbedBuilder setDescription(String value) { super.setDescription(value); return this; }
    /**
     * Sets the description, as JDA's {@code CharSequence} overload.
     *
     * @param value the description, or {@code null} to clear it
     * @return this builder
     */
    public EmbedBuilder setDescription(CharSequence value) { return setDescription(value == null ? null : value.toString()); }
    @Override public EmbedBuilder appendDescription(CharSequence value) { super.appendDescription(value); return this; }
    @Override public EmbedBuilder setColor(int color) { super.setColor(color); return this; }
    @Override public EmbedBuilder setColor(java.awt.Color color) { super.setColor(color); return this; }
    @Override public EmbedBuilder setTimestamp(Instant timestamp) { super.setTimestamp(timestamp); return this; }
    @Override public EmbedBuilder setAuthor(String name) { super.setAuthor(name); return this; }
    @Override public EmbedBuilder setAuthor(String name, String url) { super.setAuthor(name, url); return this; }
    @Override public EmbedBuilder setAuthor(String name, String url, String icon) { super.setAuthor(name, url, icon); return this; }
    @Override public EmbedBuilder setFooter(String text) { super.setFooter(text); return this; }
    @Override public EmbedBuilder setFooter(String text, String icon) { super.setFooter(text, icon); return this; }
    @Override public EmbedBuilder setImage(String url) { super.setImage(url); return this; }
    @Override public EmbedBuilder setThumbnail(String url) { super.setThumbnail(url); return this; }
    @Override public EmbedBuilder addField(String name, String value, boolean inline) { super.addField(name, value, inline); return this; }
    @Override public EmbedBuilder clear() { super.clear(); return this; }
    @Override public EmbedBuilder clearFields() { super.clearFields(); return this; }
}
