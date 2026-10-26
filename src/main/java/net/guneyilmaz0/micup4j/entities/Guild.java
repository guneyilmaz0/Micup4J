package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.internal.Json;
import java.util.*;

/** Immutable snapshot. Members and visible channels never imply a complete guild roster. */
public record Guild(String id, String name, String ownerId, boolean available,
                    List<Role> roles, List<GuildChannel> channels, List<Member> members,
                    boolean rolesComplete, boolean channelsComplete, net.guneyilmaz0.micup4j.Micup client) implements ISnowflake {
    public Guild(String id, String name, String ownerId, boolean available, List<Role> roles,
                 List<GuildChannel> channels, List<Member> members, boolean rolesComplete, boolean channelsComplete) {
        this(id, name, ownerId, available, roles, channels, members, rolesComplete, channelsComplete, null);
    }
    public Guild withClient(net.guneyilmaz0.micup4j.Micup client) {
        return new Guild(id, name, ownerId, available, roles, channels, members.stream().map(m -> m.withClient(client)).toList(),
                rolesComplete, channelsComplete, client);
    }
    @Override public String getId() { return id; }
    public String getName() { return name; }
    public String getOwnerId() { return ownerId; }
    public boolean isAvailable() { return available; }
    public List<Role> getRoles() { return roles.stream().sorted(Role.HIERARCHY).toList(); }
    public List<Member> getMembers() { return members; }
    public List<GuildChannel> getChannels() { return channels; }
    public Member getMemberById(String id) { return findMemberById(id).orElse(null); }
    public Member getMemberById(long id) { return getMemberById(Long.toUnsignedString(id)); }
    public Role getRoleById(String id) { return findRoleById(id).orElse(null); }
    public Role getRoleById(long id) { return getRoleById(Long.toUnsignedString(id)); }
    public GuildChannel getChannelById(String id) { return findChannelById(id).orElse(null); }
    public GuildChannel getChannelById(long id) { return getChannelById(Long.toUnsignedString(id)); }
    public net.guneyilmaz0.micup4j.entities.channel.concrete.TextChannel getTextChannelById(String id) {
        GuildChannel channel = getChannelById(id);
        return channel == null || channel.rawType() != 0 ? null
                : new net.guneyilmaz0.micup4j.entities.channel.concrete.TextChannel(getJDA(), id, this.id, channel.name());
    }
    public net.guneyilmaz0.micup4j.entities.channel.concrete.TextChannel getTextChannelById(long id) { return getTextChannelById(Long.toUnsignedString(id)); }
    public net.guneyilmaz0.micup4j.Micup getJDA() {
        if (client == null) throw new IllegalStateException("Detached guild has no client");
        return client;
    }
    public net.guneyilmaz0.micup4j.requests.restaction.CommandListUpdateAction updateCommands() { return getJDA().updateGuildCommands(id); }
    public net.guneyilmaz0.micup4j.requests.RestAction<net.guneyilmaz0.micup4j.interactions.commands.Command> upsertCommand(
            net.guneyilmaz0.micup4j.interactions.commands.build.CommandData command) { return getJDA().upsertGuildCommand(id, command); }
    public net.guneyilmaz0.micup4j.requests.RestAction<List<net.guneyilmaz0.micup4j.interactions.commands.Command>> retrieveCommands() {
        return getJDA().retrieveGuildCommands(id);
    }
    public net.guneyilmaz0.micup4j.requests.RestAction<Void> deleteCommandById(String commandId) { return getJDA().deleteGuildCommandById(id, commandId); }
    public net.guneyilmaz0.micup4j.requests.RestAction<Void> deleteCommandById(long commandId) { return deleteCommandById(Long.toUnsignedString(commandId)); }
    public net.guneyilmaz0.micup4j.requests.RestAction<Member> retrieveMemberById(String userId) { return getJDA().retrieveMemberById(id, userId); }
    public net.guneyilmaz0.micup4j.requests.RestAction<Member> retrieveMemberById(long userId) { return retrieveMemberById(Long.toUnsignedString(userId)); }
    public Member getSelfMember() {
        Member result = getMemberById(getJDA().getSelfUser().id());
        if (result == null) throw new IllegalStateException("Self member is not cached; retrieve the current membership");
        return result;
    }
    public Guild {
        Json.id(id); Objects.requireNonNull(name);
        if (ownerId != null) Json.id(ownerId);
        roles = List.copyOf(roles); channels = List.copyOf(channels); members = List.copyOf(members);
        if (roles.stream().anyMatch(r -> !r.guildId().equals(id)) || channels.stream().anyMatch(c -> !c.guildId().equals(id))
                || members.stream().anyMatch(m -> !m.guildId().equals(id)))
            throw new IllegalArgumentException("Entity belongs to another guild");
    }
    public Optional<Member> findMemberById(String userId) { return members.stream().filter(m -> m.getId().equals(userId)).findFirst(); }
    public Optional<Role> findRoleById(String roleId) { return roles.stream().filter(r -> r.id().equals(roleId)).findFirst(); }
    public Optional<GuildChannel> findChannelById(String channelId) { return channels.stream().filter(c -> c.id().equals(channelId)).findFirst(); }
    public static Guild fromJson(JsonNode data) {
        var properties = data.path("properties").isObject() ? data.path("properties") : data;
        String id = Json.id(data.path("id").asText(properties.path("id").asText()));
        var roles = new ArrayList<Role>(); var channels = new ArrayList<GuildChannel>(); var members = new ArrayList<Member>();
        for (var role : data.path("roles")) roles.add(Role.fromJson(id, role));
        for (var channel : data.path("channels")) channels.add(GuildChannel.fromJson(id, channel));
        for (var member : data.path("members")) members.add(Member.fromJson(id, member));
        return new Guild(id, properties.path("name").asText(""), properties.path("owner_id").asText(null),
                !data.path("unavailable").asBoolean(), roles, channels, members,
                data.path("roles").isArray(), data.path("channels").isArray());
    }
}
