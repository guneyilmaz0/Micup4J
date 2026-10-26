package net.guneyilmaz0.micup4j.interactions.components.selections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.entities.channel.ChannelType;
import net.guneyilmaz0.micup4j.internal.Json;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * An immutable select menu of users, roles, mentionables or channels (types 5-8), modelled on JDA's
 * {@code EntitySelectMenu}. Micup offers entities in the guild that the clicker can see.
 */
public final class EntitySelectMenu extends SelectMenu {
    /** What an entity menu offers. */
    public enum SelectTarget {
        /** Guild members. */
        USER,
        /** Roles. */
        ROLE,
        /** Channels; cannot be combined with other targets. */
        CHANNEL
    }

    /**
     * A value chosen in advance.
     *
     * @param id   the entity id
     * @param type {@code user}, {@code role} or {@code channel}
     */
    public record DefaultValue(String id, SelectTarget type) {
        /**
         * Validates the default value.
         *
         * @throws IllegalArgumentException if the id is invalid
         */
        public DefaultValue { Json.id(id); java.util.Objects.requireNonNull(type); }
        /**
         * Creates a default user.
         *
         * @param id the user id
         * @return the default value
         */
        public static DefaultValue user(String id) { return new DefaultValue(id, SelectTarget.USER); }
        /**
         * Creates a default role.
         *
         * @param id the role id
         * @return the default value
         */
        public static DefaultValue role(String id) { return new DefaultValue(id, SelectTarget.ROLE); }
        /**
         * Creates a default channel.
         *
         * @param id the channel id
         * @return the default value
         */
        public static DefaultValue channel(String id) { return new DefaultValue(id, SelectTarget.CHANNEL); }
        /**
         * Creates a default value from an entity: roles, guild channels, and users or members otherwise.
         *
         * @param entity the entity
         * @return the default value
         */
        public static DefaultValue from(net.guneyilmaz0.micup4j.entities.ISnowflake entity) {
            return new DefaultValue(entity.getId(), entity instanceof net.guneyilmaz0.micup4j.entities.Role ? SelectTarget.ROLE
                    : entity instanceof net.guneyilmaz0.micup4j.entities.GuildChannel ? SelectTarget.CHANNEL : SelectTarget.USER);
        }
        ObjectNode toJson() { return Json.object().put("id", id).put("type", type.name().toLowerCase(java.util.Locale.ROOT)); }
    }

    private final Set<SelectTarget> targets;
    private final List<ChannelType> channelTypes;
    private final List<DefaultValue> defaults;

    private EntitySelectMenu(String id, String placeholder, int min, int max, boolean disabled, Set<SelectTarget> targets,
                             List<ChannelType> channelTypes, List<DefaultValue> defaults) {
        super(id, placeholder, min, max, disabled);
        if (targets.isEmpty()) throw new IllegalArgumentException("An entity select needs a target");
        if (targets.contains(SelectTarget.CHANNEL) && targets.size() > 1)
            throw new IllegalArgumentException("Channels cannot be combined with users or roles");
        if (!channelTypes.isEmpty() && !targets.contains(SelectTarget.CHANNEL))
            throw new IllegalArgumentException("Channel types apply only to channel selects");
        if (defaults.size() > max) throw new IllegalArgumentException("More default values than max values");
        for (DefaultValue value : defaults)
            if (!targets.contains(value.type())) throw new IllegalArgumentException("Default value type does not match the select targets");
        this.targets = Set.copyOf(targets); this.channelTypes = List.copyOf(channelTypes); this.defaults = List.copyOf(defaults);
    }

    /**
     * Starts a builder.
     *
     * @param customId the custom id
     * @param target   the first target
     * @param more     further targets; only {@code USER} and {@code ROLE} combine
     * @return the builder
     */
    public static Builder create(String customId, SelectTarget target, SelectTarget... more) {
        var set = EnumSet.of(target, more);
        return new Builder(customId, set);
    }

    /**
     * Starts a builder.
     *
     * @param customId the custom id
     * @param targets  the targets
     * @return the builder
     */
    public static Builder create(String customId, Collection<SelectTarget> targets) { return new Builder(customId, EnumSet.copyOf(targets)); }

    @Override public int getTypeRaw() {
        if (targets.contains(SelectTarget.CHANNEL)) return 8;
        if (targets.size() == 2) return 7;
        return targets.contains(SelectTarget.ROLE) ? 6 : 5;
    }
    /**
     * Returns the offered entity kinds.
     *
     * @return the offered entity kinds
     */
    public Set<SelectTarget> getEntityTypes() { return targets; }
    /**
     * Returns the allowed channel types; empty for no restriction.
     *
     * @return the allowed channel types; empty for no restriction
     */
    public List<ChannelType> getChannelTypes() { return channelTypes; }
    /**
     * Returns the values chosen in advance.
     *
     * @return the values chosen in advance
     */
    public List<DefaultValue> getDefaultValues() { return defaults; }
    @Override public EntitySelectMenu withDisabled(boolean disabled) {
        return new EntitySelectMenu(id, placeholder, minValues, maxValues, disabled, targets, channelTypes, defaults);
    }

    @Override public ObjectNode toJson() {
        var node = baseJson();
        if (!channelTypes.isEmpty()) { var array = node.putArray("channel_types"); channelTypes.forEach(type -> array.add(type.getId())); }
        if (!defaults.isEmpty()) { var array = node.putArray("default_values"); defaults.forEach(value -> array.add(value.toJson())); }
        return node;
    }

    /**
     * Parses an entity select menu from a message payload.
     *
     * @param node the select menu object of type 5-8
     * @return the menu
     * @throws IllegalArgumentException if it is not a valid entity select
     */
    public static EntitySelectMenu fromJson(JsonNode node) {
        int type = node.path("type").asInt();
        Set<SelectTarget> targets = switch (type) {
            case 5 -> EnumSet.of(SelectTarget.USER);
            case 6 -> EnumSet.of(SelectTarget.ROLE);
            case 7 -> EnumSet.of(SelectTarget.USER, SelectTarget.ROLE);
            case 8 -> EnumSet.of(SelectTarget.CHANNEL);
            default -> throw new IllegalArgumentException("Not an entity select");
        };
        var channels = new ArrayList<ChannelType>();
        node.path("channel_types").forEach(value -> channels.add(ChannelType.fromId(value.asInt())));
        var defaults = new ArrayList<DefaultValue>();
        node.path("default_values").forEach(value -> defaults.add(new DefaultValue(value.path("id").asText(),
                SelectTarget.valueOf(value.path("type").asText().toUpperCase(java.util.Locale.ROOT)))));
        return new EntitySelectMenu(node.path("custom_id").asText(), node.hasNonNull("placeholder") ? node.path("placeholder").asText() : null,
                node.path("min_values").asInt(1), node.path("max_values").asInt(1), node.path("disabled").asBoolean(), targets, channels, defaults);
    }

    @Override public boolean equals(Object other) { return other instanceof EntitySelectMenu menu && toJson().equals(menu.toJson()); }
    @Override public int hashCode() { return toJson().hashCode(); }

    /** Fluent builder for {@link EntitySelectMenu}. */
    public static final class Builder extends SelectMenu.Builder<EntitySelectMenu, Builder> {
        private final Set<SelectTarget> targets;
        private final List<ChannelType> channelTypes = new ArrayList<>();
        private final List<DefaultValue> defaults = new ArrayList<>();

        Builder(String id, Set<SelectTarget> targets) { super(id); this.targets = EnumSet.copyOf(targets); }
        /**
         * Restricts a channel select to some channel types.
         *
         * @param types the types
         * @return this builder
         */
        public Builder setChannelTypes(ChannelType... types) { return setChannelTypes(Arrays.asList(types)); }
        /**
         * Restricts a channel select to some channel types.
         *
         * @param types the types; replaces earlier ones
         * @return this builder
         */
        public Builder setChannelTypes(Collection<ChannelType> types) { channelTypes.clear(); channelTypes.addAll(types); return this; }
        /**
         * Sets the values chosen in advance.
         *
         * @param values at most the maximum number of values, matching the targets
         * @return this builder
         */
        public Builder setDefaultValues(DefaultValue... values) { return setDefaultValues(Arrays.asList(values)); }
        /**
         * Sets the values chosen in advance.
         *
         * @param values at most the maximum number of values, matching the targets; replaces earlier ones
         * @return this builder
         */
        public Builder setDefaultValues(Collection<DefaultValue> values) { defaults.clear(); defaults.addAll(values); return this; }
        @Override public EntitySelectMenu build() {
            return new EntitySelectMenu(id, placeholder, minValues, maxValues, disabled, targets, channelTypes, defaults);
        }
    }
}
