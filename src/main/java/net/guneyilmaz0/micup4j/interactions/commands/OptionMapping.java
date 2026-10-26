package net.guneyilmaz0.micup4j.interactions.commands;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.entities.*;
import net.guneyilmaz0.micup4j.internal.Json;
import java.util.Objects;

/** Copied option data with typed conversions and locally resolved entities; never performs HTTP. */
public final class OptionMapping {
    private final Micup client;
    private final String guildId, name;
    private final OptionType type;
    private final JsonNode value, resolved;
    public OptionMapping(Micup client, String guildId, JsonNode data, JsonNode resolved) {
        this.client = Objects.requireNonNull(client); this.guildId = guildId;
        name = data.path("name").asText(); type = OptionType.fromKey(data.path("type").asInt(-1));
        value = data.path("value").deepCopy(); this.resolved = resolved.deepCopy();
        if (!value.isValueNode() || value.isNull()) throw new IllegalArgumentException("Option has no scalar value");
    }
    public String getName() { return name; }
    public OptionType getType() { return type; }
    public String getAsString() { return value.asText(); }
    public boolean getAsBoolean() {
        if (type != OptionType.BOOLEAN || !value.isBoolean()) throw new IllegalStateException("Option is not a boolean");
        return value.booleanValue();
    }
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
    public int getAsInt() { return Math.toIntExact(getAsLong()); }
    public double getAsDouble() {
        if (type != OptionType.STRING && type != OptionType.INTEGER && type != OptionType.NUMBER)
            throw new IllegalStateException("Option cannot be converted to a double");
        double result = Double.parseDouble(getAsString());
        if (!Double.isFinite(result)) throw new IllegalStateException("Option is not finite");
        return result;
    }
    public User getAsUser() {
        require(OptionType.USER);
        JsonNode user = resolved.path("users").path(Json.id(getAsString()));
        if (!user.isObject()) throw new IllegalStateException("Option user was not resolved");
        return User.fromJson(user).withClient(client);
    }
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
