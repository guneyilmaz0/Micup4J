package com.swadedev.micup4j.interactions.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.swadedev.micup4j.Micup;
import com.swadedev.micup4j.entities.*;
import com.swadedev.micup4j.internal.Json;
import java.util.Objects;

/** Copied option data with typed conversions and locally resolved entities; never performs HTTP. */
public final class OptionMapping {
    private final Micup client;
    private final String guildId, name;
    private final OptionType type;
    private final JsonNode value, resolved;
    /**
     * Creates a mapping; called by the library.
     *
     * @param client   the session
     * @param guildId  the interaction's guild, or {@code null}
     * @param data     the option value object
     * @param resolved the interaction's resolved data
     */
    public OptionMapping(Micup client, String guildId, JsonNode data, JsonNode resolved) {
        this.client = Objects.requireNonNull(client); this.guildId = guildId;
        name = data.path("name").asText(); type = OptionType.fromKey(data.path("type").asInt(-1));
        value = data.path("value").deepCopy(); this.resolved = resolved.deepCopy();
        if (!value.isValueNode() || value.isNull()) throw new IllegalArgumentException("Option has no scalar value");
    }
    /**
     * Returns the option name.
     *
     * @return the option name
     */
    public String getName() { return name; }
    /**
     * Returns the option type.
     *
     * @return the option type
     */
    public OptionType getType() { return type; }
    /**
     * Returns the raw value as text; ids for entity options.
     *
     * @return the raw value as text; ids for entity options
     */
    public String getAsString() { return value.asText(); }
    /**
     * Returns a boolean option's value.
     *
     * @return the value
     * @throws IllegalStateException if this is not a boolean option
     */
    public boolean getAsBoolean() {
        if (type != OptionType.BOOLEAN || !value.isBoolean()) throw new IllegalStateException("Option is not a boolean");
        return value.booleanValue();
    }
    /**
     * Returns the value as a long: integers, numeric strings, and entity options as unsigned ids.
     *
     * @return the value
     * @throws IllegalStateException if the value cannot be converted
     * @throws NumberFormatException if a string option is not numeric
     */
    public long getAsLong() {
        return switch (type) {
            case USER, ROLE, CHANNEL -> Long.parseUnsignedLong(Json.id(getAsString()));
            case STRING -> Long.parseLong(getAsString());
            case INTEGER -> {
                if (!value.isIntegralNumber() || !value.canConvertToLong()) throw new IllegalStateException("Option is not a signed integer");
                yield value.longValue();
            }
            default -> throw new IllegalStateException("Option cannot be converted to a long");
        };
    }
    /**
     * Returns the value as an int.
     *
     * @return the value
     * @throws ArithmeticException if it does not fit
     * @throws IllegalStateException if the value cannot be converted
     */
    public int getAsInt() { return Math.toIntExact(getAsLong()); }
    /**
     * Returns the value of a string, integer or number option as a double.
     *
     * @return the finite value
     * @throws IllegalStateException if the type cannot be converted or the value is not finite
     */
    public double getAsDouble() {
        if (type != OptionType.STRING && type != OptionType.INTEGER && type != OptionType.NUMBER)
            throw new IllegalStateException("Option cannot be converted to a double");
        double result = Double.parseDouble(getAsString());
        if (!Double.isFinite(result)) throw new IllegalStateException("Option is not finite");
        return result;
    }
    /**
     * Returns the resolved user of a user option.
     *
     * @return the user
     * @throws IllegalStateException if this is not a user option or the user was not resolved
     */
    public User getAsUser() {
        require(OptionType.USER);
        JsonNode user = resolved.path("users").path(Json.id(getAsString()));
        if (!user.isObject()) throw new IllegalStateException("Option user was not resolved");
        return User.fromJson(user).withClient(client);
    }
    /**
     * Returns the resolved member of a user option.
     *
     * @return the member, or {@code null} outside a guild or when the user is not a member
     * @throws IllegalStateException if this is not a user option
     */
    public Member getAsMember() {
        require(OptionType.USER);
        if (guildId == null) return null;
        JsonNode member = resolved.path("members").path(Json.id(getAsString()));
        if (!member.isObject()) return null;
        var data = (com.fasterxml.jackson.databind.node.ObjectNode) member.deepCopy();
        data.set("user", Json.MAPPER.valueToTree(getAsUserPayload()));
        return Member.fromJson(guildId, data).withClient(client);
    }
    private JsonNode getAsUserPayload() {
        var user = resolved.path("users").path(Json.id(getAsString()));
        if (!user.isObject()) throw new IllegalStateException("Option user was not resolved");
        return user.deepCopy();
    }
    /**
     * Returns the role of a role option, from the resolved data or the cache.
     *
     * @return the role
     * @throws IllegalStateException if this is not a role option or the role is unavailable
     */
    public Role getAsRole() {
        require(OptionType.ROLE);
        if (guildId == null) throw new IllegalStateException("Role option has no guild");
        var role = resolved.path("roles").path(Json.id(getAsString()));
        if (role.isObject()) return Role.fromJson(guildId, role);
        Guild guild = client.getGuildById(guildId);
        Role cached = guild == null ? null : guild.getRoleById(getAsString());
        if (cached == null) throw new IllegalStateException("Option role was not resolved");
        return cached;
    }
    /**
     * Returns the channel of a channel option, from the resolved data or the cache.
     *
     * @return the channel
     * @throws IllegalStateException if this is not a channel option or the channel is unavailable
     */
    public GuildChannel getAsChannel() {
        require(OptionType.CHANNEL);
        if (guildId == null) throw new IllegalStateException("Channel option has no guild");
        var channel = resolved.path("channels").path(Json.id(getAsString()));
        if (channel.path("permission_overwrites").isArray()) return GuildChannel.fromJson(guildId, channel);
        Guild guild = client.getGuildById(guildId);
        GuildChannel cached = guild == null ? null : guild.getChannelById(getAsString());
        if (cached == null) throw new IllegalStateException("Complete option channel was not resolved");
        return cached;
    }
    private void require(OptionType expected) {
        if (type != expected) throw new IllegalStateException("Option has an incompatible type");
    }
    @Override public String toString() { return "OptionMapping[name=" + name + ", type=" + type + "]"; }
}
