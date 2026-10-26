package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.internal.Json;
import java.net.URI;
import java.time.Instant;
import java.util.Set;

/** Mutable builder using Micup's documented rich-embed field limits; build creates a snapshot. */
public class EmbedBuilder {
    private final ObjectNode data = Json.object();
    public EmbedBuilder() {}
    public EmbedBuilder(MessageEmbed embed) { data.setAll(embed.toJson()); }
    public EmbedBuilder(EmbedBuilder builder) { data.setAll(builder.data.deepCopy()); }
    public EmbedBuilder clear() { data.removeAll(); return this; }
    public EmbedBuilder clearFields() { data.remove("fields"); return this; }
    public EmbedBuilder setAuthor(String name) { return setAuthor(name, null, null); }
    public EmbedBuilder setAuthor(String name, String url) { return setAuthor(name, url, null); }
    public EmbedBuilder setFooter(String text) { return setFooter(text, null); }
    public EmbedBuilder setColor(java.awt.Color color) {
        if (color == null) { data.remove("color"); return this; }
        return setColor(color.getRGB() & 0xFFFFFF);
    }
    public EmbedBuilder appendDescription(CharSequence text) {
        return setDescription(data.path("description").asText("") + java.util.Objects.requireNonNull(text));
    }
    public EmbedBuilder setTitle(String title) { return setTitle(title, null); }
    public EmbedBuilder setTitle(String title, String url) { text(data, "title", title, 256, false); url(data, "url", url, false); return this; }
    public EmbedBuilder setDescription(String text) { text(data, "description", text, 4096, false); return this; }
    public EmbedBuilder setColor(int color) {
        if (color < 0 || color > 0xFFFFFF) throw new IllegalArgumentException("Color must be a 24-bit RGB value");
        data.put("color", color); return this;
    }
    public EmbedBuilder setTimestamp(Instant timestamp) {
        if (timestamp == null) data.remove("timestamp"); else data.put("timestamp", timestamp.toString());
        return this;
    }
    public EmbedBuilder setAuthor(String name, String url, String iconUrl) {
        if (name == null) { data.remove("author"); return this; }
        var author = Json.object(); text(author, "name", name, 256, true);
        url(author, "url", url, false); url(author, "icon_url", iconUrl, false); data.set("author", author); return this;
    }
    public EmbedBuilder setFooter(String text, String iconUrl) {
        if (text == null) { data.remove("footer"); return this; }
        var footer = Json.object(); text(footer, "text", text, 2048, true);
        url(footer, "icon_url", iconUrl, false); data.set("footer", footer); return this;
    }
    public EmbedBuilder setImage(String value) { return media("image", value); }
    public EmbedBuilder setThumbnail(String value) { return media("thumbnail", value); }
    private EmbedBuilder media(String field, String value) {
        if (value == null) data.remove(field);
        else { var media = Json.object(); url(media, "url", value, true); data.set(field, media); }
        return this;
    }
    public EmbedBuilder addField(String name, String value, boolean inline) {
        if (data.path("fields").size() >= 25) throw new IllegalArgumentException("An embed supports at most 25 fields");
        var field = Json.object(); text(field, "name", name, 256, true);
        if (value == null || value.codePointCount(0, value.length()) > 1024) throw new IllegalArgumentException("Invalid field value length");
        field.put("value", value).put("inline", inline); data.withArray("fields").add(field); return this;
    }
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
