package com.swadedev.micup4j.entities;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swadedev.micup4j.internal.Json;
import java.net.URI;
import java.time.Instant;
import java.util.Set;

/** Mutable builder using Micup's documented rich-embed field limits; build creates a snapshot. */
public class EmbedBuilder {
    private final ObjectNode data = Json.object();
    /** Creates an empty builder. */
    public EmbedBuilder() {}
    /**
     * Creates a builder copying a built embed.
     *
     * @param embed the embed to copy
     */
    public EmbedBuilder(MessageEmbed embed) { data.setAll(embed.toJson()); }
    /**
     * Creates a builder copying another builder.
     *
     * @param builder the builder to copy
     */
    public EmbedBuilder(EmbedBuilder builder) { data.setAll(builder.data.deepCopy()); }
    /**
     * Removes every field.
     *
     * @return this builder
     */
    public EmbedBuilder clear() { data.removeAll(); return this; }
    /**
     * Removes every embed field added with {@link #addField(String, String, boolean)}.
     *
     * @return this builder
     */
    public EmbedBuilder clearFields() { data.remove("fields"); return this; }
    /**
     * Sets the author name.
     *
     * @param name 1-256 characters, or {@code null} to remove the author
     * @return this builder
     */
    public EmbedBuilder setAuthor(String name) { return setAuthor(name, null, null); }
    /**
     * Sets the author name and link.
     *
     * @param name 1-256 characters, or {@code null} to remove the author
     * @param url  an http(s) link, or {@code null}
     * @return this builder
     */
    public EmbedBuilder setAuthor(String name, String url) { return setAuthor(name, url, null); }
    /**
     * Sets the footer text.
     *
     * @param text 1-2048 characters, or {@code null} to remove the footer
     * @return this builder
     */
    public EmbedBuilder setFooter(String text) { return setFooter(text, null); }
    /**
     * Sets the side color.
     *
     * @param color the color, or {@code null} for the default
     * @return this builder
     */
    public EmbedBuilder setColor(java.awt.Color color) {
        if (color == null) { data.remove("color"); return this; }
        return setColor(color.getRGB() & 0xFFFFFF);
    }
    /**
     * Appends to the description.
     *
     * @param text the text to append; the result must stay within 4096 characters
     * @return this builder
     */
    public EmbedBuilder appendDescription(CharSequence text) {
        return setDescription(data.path("description").asText("") + java.util.Objects.requireNonNull(text));
    }
    /**
     * Sets the title.
     *
     * @param title at most 256 characters, or {@code null} to remove it
     * @return this builder
     */
    public EmbedBuilder setTitle(String title) { return setTitle(title, null); }
    /**
     * Sets the title and its link.
     *
     * @param title at most 256 characters, or {@code null} to remove it
     * @param url   an http(s) link, or {@code null}
     * @return this builder
     */
    public EmbedBuilder setTitle(String title, String url) { text(data, "title", title, 256, false); url(data, "url", url, false); return this; }
    /**
     * Sets the description.
     *
     * @param text at most 4096 characters, or {@code null} to remove it
     * @return this builder
     */
    public EmbedBuilder setDescription(String text) { text(data, "description", text, 4096, false); return this; }
    /**
     * Sets the side color.
     *
     * @param color a 24-bit RGB value
     * @return this builder
     * @throws IllegalArgumentException if the value is outside 0-0xFFFFFF
     */
    public EmbedBuilder setColor(int color) {
        if (color < 0 || color > 0xFFFFFF) throw new IllegalArgumentException("Color must be a 24-bit RGB value");
        data.put("color", color); return this;
    }
    /**
     * Sets the timestamp shown in the footer.
     *
     * @param timestamp the instant, or {@code null} to remove it
     * @return this builder
     */
    public EmbedBuilder setTimestamp(Instant timestamp) {
        if (timestamp == null) data.remove("timestamp"); else data.put("timestamp", timestamp.toString());
        return this;
    }
    /**
     * Sets the author.
     *
     * @param name    1-256 characters, or {@code null} to remove the author
     * @param url     an http(s) link, or {@code null}
     * @param iconUrl an http(s) icon URL, or {@code null}
     * @return this builder
     */
    public EmbedBuilder setAuthor(String name, String url, String iconUrl) {
        if (name == null) { data.remove("author"); return this; }
        var author = Json.object(); text(author, "name", name, 256, true);
        url(author, "url", url, false); url(author, "icon_url", iconUrl, false); data.set("author", author); return this;
    }
    /**
     * Sets the footer.
     *
     * @param text    1-2048 characters, or {@code null} to remove the footer
     * @param iconUrl an http(s) icon URL, or {@code null}
     * @return this builder
     */
    public EmbedBuilder setFooter(String text, String iconUrl) {
        if (text == null) { data.remove("footer"); return this; }
        var footer = Json.object(); text(footer, "text", text, 2048, true);
        url(footer, "icon_url", iconUrl, false); data.set("footer", footer); return this;
    }
    /**
     * Sets the large image.
     *
     * @param value an http(s) or {@code attachment://} URL, or {@code null} to remove it
     * @return this builder
     */
    public EmbedBuilder setImage(String value) { return media("image", value); }
    /**
     * Sets the thumbnail.
     *
     * @param value an http(s) or {@code attachment://} URL, or {@code null} to remove it
     * @return this builder
     */
    public EmbedBuilder setThumbnail(String value) { return media("thumbnail", value); }
    private EmbedBuilder media(String field, String value) {
        if (value == null) data.remove(field);
        else { var media = Json.object(); url(media, "url", value, true); data.set(field, media); }
        return this;
    }
    /**
     * Adds a field.
     *
     * @param name   1-256 characters
     * @param value  at most 1024 characters
     * @param inline whether the field may share a row
     * @return this builder
     * @throws IllegalArgumentException if a length is invalid or the embed already has 25 fields
     */
    public EmbedBuilder addField(String name, String value, boolean inline) {
        if (data.path("fields").size() >= 25) throw new IllegalArgumentException("An embed supports at most 25 fields");
        var field = Json.object(); text(field, "name", name, 256, true);
        if (value == null || value.codePointCount(0, value.length()) > 1024) throw new IllegalArgumentException("Invalid field value length");
        field.put("value", value).put("inline", inline); data.withArray("fields").add(field); return this;
    }
    /**
     * Builds an immutable snapshot; the builder stays usable.
     *
     * @return the embed
     * @throws IllegalStateException if no field is set
     */
    public MessageEmbed build() {
        if (data.isEmpty()) throw new IllegalStateException("Embed must contain at least one field");
        return new MessageEmbed(data);
    }
    private static void text(ObjectNode target, String key, String value, int max, boolean required) {
        if (value == null || value.isEmpty()) {
            if (required) throw new IllegalArgumentException("Embed " + key + " is required");
            target.remove(key); return;
        }
        if (value.codePointCount(0, value.length()) > max) throw new IllegalArgumentException("Embed " + key + " exceeds its character limit");
        target.put(key, value);
    }
    private static void url(ObjectNode target, String field, String value, boolean attachment) {
        if (value == null) { target.remove(field); return; }
        try {
            URI uri = URI.create(value);
            boolean reference = attachment && value.matches("(?i)attachment://[^/]+\\.(png|jpe?g|webp|gif)");
            boolean web = Set.of("http", "https").contains(java.util.Objects.toString(uri.getScheme(), "").toLowerCase(java.util.Locale.ROOT)) && uri.getHost() != null && uri.getUserInfo() == null;
            if (value.length() > 2048 || !(web || reference)) throw new IllegalArgumentException();
        } catch (Exception invalid) { throw new IllegalArgumentException("Invalid embed URL"); }
        target.put(field, value);
    }
}
