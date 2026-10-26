package net.guneyilmaz0.micup4j.interactions.components.buttons;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.entities.emoji.Emoji;
import net.guneyilmaz0.micup4j.interactions.components.ActionHandle;
import net.guneyilmaz0.micup4j.interactions.components.ComponentChecks;
import net.guneyilmaz0.micup4j.interactions.components.ItemComponent;
import net.guneyilmaz0.micup4j.internal.Json;
import java.net.URI;

/**
 * An immutable message button, modelled on JDA's {@code Button}. A link button opens its URL and sends nothing to the
 * bot; every other style sends its custom id back as a {@code ButtonInteractionEvent}. The custom id is untrusted user
 * input when it comes back: validate it, and never put secrets or permissions in it.
 */
public final class Button implements ItemComponent, ActionHandle {
    /** Longest label, in characters. */
    public static final int LABEL_MAX_LENGTH = 80;
    /** Longest custom id, in characters. */
    public static final int ID_MAX_LENGTH = 100;
    /** Longest link URL, in characters. */
    public static final int URL_MAX_LENGTH = 512;

    private final ButtonStyle style;
    private final String id, url, label;
    private final Emoji emoji;
    private final boolean disabled;

    private Button(ButtonStyle style, String id, String url, String label, Emoji emoji, boolean disabled) {
        if (style == null || style == ButtonStyle.UNKNOWN) throw new IllegalArgumentException("A known button style is required");
        if ((label == null || label.isEmpty()) && emoji == null) throw new IllegalArgumentException("A button needs a label or an emoji");
        if (label != null) ComponentChecks.length("Button label", label, 0, LABEL_MAX_LENGTH);
        if (style == ButtonStyle.LINK) {
            if (id != null) throw new IllegalArgumentException("A link button has no custom id");
            checkUrl(url);
        } else {
            if (url != null) throw new IllegalArgumentException("Only link buttons have a URL");
            ComponentChecks.customId(id);
        }
        this.style = style; this.id = id; this.url = url; this.label = label == null || label.isEmpty() ? null : label;
        this.emoji = emoji; this.disabled = disabled;
    }

    private static void checkUrl(String url) {
        ComponentChecks.length("Button URL", url, 1, URL_MAX_LENGTH);
        URI uri;
        try { uri = URI.create(url); } catch (IllegalArgumentException error) { throw new IllegalArgumentException("Button URL is invalid"); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("Button URL must be an http(s) URL without credentials");
    }

    /**
     * Creates a primary button with a label.
     *
     * @param id    the custom id sent back on click, 1-100 characters
     * @param label the label, 1-80 characters
     * @return the button
     * @throws IllegalArgumentException if a value violates Micup's limits
     */
    public static Button primary(String id, String label) { return of(ButtonStyle.PRIMARY, id, label); }
    /**
     * Creates a primary button showing only an emoji.
     *
     * @param id    the custom id sent back on click, 1-100 characters
     * @param emoji the emoji
     * @return the button
     * @throws IllegalArgumentException if a value violates Micup's limits
     */
    public static Button primary(String id, Emoji emoji) { return of(ButtonStyle.PRIMARY, id, null, emoji); }
    /**
     * Creates a secondary button with a label.
     *
     * @param id    the custom id sent back on click, 1-100 characters
     * @param label the label, 1-80 characters
     * @return the button
     * @throws IllegalArgumentException if a value violates Micup's limits
     */
    public static Button secondary(String id, String label) { return of(ButtonStyle.SECONDARY, id, label); }
    /**
     * Creates a secondary button showing only an emoji.
     *
     * @param id    the custom id sent back on click, 1-100 characters
     * @param emoji the emoji
     * @return the button
     * @throws IllegalArgumentException if a value violates Micup's limits
     */
    public static Button secondary(String id, Emoji emoji) { return of(ButtonStyle.SECONDARY, id, null, emoji); }
    /**
     * Creates a success button with a label.
     *
     * @param id    the custom id sent back on click, 1-100 characters
     * @param label the label, 1-80 characters
     * @return the button
     * @throws IllegalArgumentException if a value violates Micup's limits
     */
    public static Button success(String id, String label) { return of(ButtonStyle.SUCCESS, id, label); }
    /**
     * Creates a success button showing only an emoji.
     *
     * @param id    the custom id sent back on click, 1-100 characters
     * @param emoji the emoji
     * @return the button
     * @throws IllegalArgumentException if a value violates Micup's limits
     */
    public static Button success(String id, Emoji emoji) { return of(ButtonStyle.SUCCESS, id, null, emoji); }
    /**
     * Creates a danger button with a label.
     *
     * @param id    the custom id sent back on click, 1-100 characters
     * @param label the label, 1-80 characters
     * @return the button
     * @throws IllegalArgumentException if a value violates Micup's limits
     */
    public static Button danger(String id, String label) { return of(ButtonStyle.DANGER, id, label); }
    /**
     * Creates a danger button showing only an emoji.
     *
     * @param id    the custom id sent back on click, 1-100 characters
     * @param emoji the emoji
     * @return the button
     * @throws IllegalArgumentException if a value violates Micup's limits
     */
    public static Button danger(String id, Emoji emoji) { return of(ButtonStyle.DANGER, id, null, emoji); }
    /**
     * Creates a link button with a label. Clicks open the URL and send nothing to the bot.
     *
     * @param url   an http(s) URL of at most 512 characters, without credentials
     * @param label the label, 1-80 characters
     * @return the button
     * @throws IllegalArgumentException if a value violates Micup's limits
     */
    public static Button link(String url, String label) { return of(ButtonStyle.LINK, url, label); }
    /**
     * Creates a link button showing only an emoji.
     *
     * @param url   an http(s) URL of at most 512 characters, without credentials
     * @param emoji the emoji
     * @return the button
     * @throws IllegalArgumentException if a value violates Micup's limits
     */
    public static Button link(String url, Emoji emoji) { return of(ButtonStyle.LINK, url, null, emoji); }

    /**
     * Creates a button.
     *
     * @param style    the style
     * @param idOrUrl  the custom id, or the URL for {@link ButtonStyle#LINK}
     * @param label    the label, or {@code null} when an emoji is given
     * @return the button
     * @throws IllegalArgumentException if a value violates Micup's limits
     */
    public static Button of(ButtonStyle style, String idOrUrl, String label) { return of(style, idOrUrl, label, null); }

    /**
     * Creates a button.
     *
     * @param style   the style
     * @param idOrUrl the custom id, or the URL for {@link ButtonStyle#LINK}
     * @param label   the label, or {@code null}
     * @param emoji   the emoji, or {@code null}
     * @return the button
     * @throws IllegalArgumentException if a value violates Micup's limits
     */
    public static Button of(ButtonStyle style, String idOrUrl, String label, Emoji emoji) {
        return style == ButtonStyle.LINK ? new Button(style, null, idOrUrl, label, emoji, false) : new Button(style, idOrUrl, null, label, emoji, false);
    }

    @Override public int getTypeRaw() { return 2; }
    @Override public String getId() { return id; }
    /**
     * Returns the style.
     *
     * @return the style
     */
    public ButtonStyle getStyle() { return style; }
    /**
     * Returns the link URL, or {@code null} for action buttons.
     *
     * @return the link URL, or {@code null} for action buttons
     */
    public String getUrl() { return url; }
    /**
     * Returns the label, or an empty string for an emoji-only button.
     *
     * @return the label, or an empty string for an emoji-only button
     */
    public String getLabel() { return label == null ? "" : label; }
    /**
     * Returns the emoji, or {@code null}.
     *
     * @return the emoji, or {@code null}
     */
    public Emoji getEmoji() { return emoji; }
    /**
     * Returns whether the button refuses clicks.
     *
     * @return whether the button refuses clicks
     */
    public boolean isDisabled() { return disabled; }

    /**
     * Returns a copy with the disabled flag set.
     *
     * @param disabled whether the button refuses clicks
     * @return the copy
     */
    public Button withDisabled(boolean disabled) { return new Button(style, id, url, label, emoji, disabled); }
    /**
     * Returns a disabled copy.
     *
     * @return a disabled copy
     */
    public Button asDisabled() { return withDisabled(true); }
    /**
     * Returns an enabled copy.
     *
     * @return an enabled copy
     */
    public Button asEnabled() { return withDisabled(false); }
    /**
     * Returns a copy with another label.
     *
     * @param label the label, or {@code null} when the button has an emoji
     * @return the copy
     */
    public Button withLabel(String label) { return new Button(style, id, url, label, emoji, disabled); }
    /**
     * Returns a copy with another emoji.
     *
     * @param emoji the emoji, or {@code null} when the button has a label
     * @return the copy
     */
    public Button withEmoji(Emoji emoji) { return new Button(style, id, url, label, emoji, disabled); }
    /**
     * Returns a copy with another custom id.
     *
     * @param id the custom id; action styles only
     * @return the copy
     */
    public Button withId(String id) { return new Button(style, id, url, label, emoji, disabled); }
    /**
     * Returns a copy with another URL.
     *
     * @param url the URL; link buttons only
     * @return the copy
     */
    public Button withUrl(String url) { return new Button(style, id, url, label, emoji, disabled); }
    /**
     * Returns a copy with another style.
     *
     * @param style the style
     * @return the copy
     * @throws IllegalArgumentException when switching between link and action styles
     */
    public Button withStyle(ButtonStyle style) {
        if ((style == ButtonStyle.LINK) != (this.style == ButtonStyle.LINK)) throw new IllegalArgumentException("Cannot switch between link and action styles");
        return new Button(style, id, url, label, emoji, disabled);
    }

    @Override public ObjectNode toJson() {
        var node = Json.object().put("type", 2).put("style", style.getKey()).put("disabled", disabled);
        if (label != null) node.put("label", label);
        if (emoji != null) node.set("emoji", emoji.toJson());
        if (id != null) node.put("custom_id", id);
        if (url != null) node.put("url", url);
        return node;
    }

    /**
     * Parses a button from a message payload.
     *
     * @param node the button object
     * @return the button
     * @throws IllegalArgumentException if it is not a valid button
     */
    public static Button fromJson(JsonNode node) {
        var style = ButtonStyle.fromKey(node.path("style").asInt());
        return new Button(style, node.hasNonNull("custom_id") ? node.path("custom_id").asText() : null,
                node.hasNonNull("url") ? node.path("url").asText() : null, node.hasNonNull("label") ? node.path("label").asText() : null,
                Emoji.fromJson(node.path("emoji")), node.path("disabled").asBoolean());
    }

    @Override public boolean equals(Object other) { return other instanceof Button button && toJson().equals(button.toJson()); }
    @Override public int hashCode() { return toJson().hashCode(); }
    @Override public String toString() { return "Button(" + style + ", " + (id != null ? id : url) + ")"; }
}
