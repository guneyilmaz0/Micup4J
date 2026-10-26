package com.swadedev.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import com.swadedev.micup4j.Micup;
import com.swadedev.micup4j.internal.Json;
import com.swadedev.micup4j.requests.RestAction;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Immutable active mentions. Payload membership is authoritative; text only determines ordering. */
public final class Mentions {
    private static final Pattern TOKENS = Pattern.compile("<(@!?|@&|#)([1-9][0-9]{0,19})>");
    private final Micup client;
    private final String guildId;
    private final List<User> users;
    private final List<Member> members;
    private final List<Role> roles;
    private final Set<String> activeRoleIds;
    private final List<GuildChannel> channels;
    private final boolean everyone;

    /**
     * Creates the view from a message payload; called by the library.
     *
     * @param client  the session
     * @param guildId the message's guild, or {@code null} outside a guild
     * @param data    the message payload
     */
    public Mentions(Micup client, String guildId, JsonNode data) {
        this.client = Objects.requireNonNull(client); this.guildId = guildId;
        everyone = data.path("mention_everyone").asBoolean();
        var userMap = new LinkedHashMap<String, User>();
        for (var entry : data.path("mentions")) {
            var user = User.fromJson(entry).withClient(client); userMap.put(Json.id(user.id()), user);
        }
        var roleIds = new LinkedHashMap<String, String>();
        for (var entry : data.path("mention_roles")) roleIds.put(Json.id(entry.asText()), entry.asText());
        activeRoleIds = Set.copyOf(roleIds.keySet());
        var channelIds = new LinkedHashMap<String, String>();
        for (var entry : data.path("mention_channels")) {
            String id = Json.id(entry.path("id").asText()); channelIds.put(id, id);
        }
        String content = data.path("content").asText("");
        users = ordered(userMap, content, "@");
        Guild guild = guildId == null ? null : client.getGuildById(guildId);
        members = guild == null ? List.of() : resolve(users.stream().map(User::id).toList(), guild::getMemberById);
        roles = guild == null ? List.of() : resolve(ordered(roleIds, content, "@&"), guild::getRoleById);
        channels = guild == null ? List.of() : resolve(ordered(channelIds, content, "#"), guild::getChannelById);
    }
    /**
     * Returns the session.
     *
     * @return the session
     */
    public Micup getJDA() { return client; }
    /**
     * Returns the mentioned users, in text order.
     *
     * @return the mentioned users, in text order
     */
    public List<User> getUsers() { return users; }
    /**
     * Returns the mentioned members found in the cache, in text order.
     *
     * @return the mentioned members found in the cache, in text order
     */
    public List<Member> getMembers() { return members; }
    /**
     * Returns the mentioned roles found in the cache, in text order.
     *
     * @return the mentioned roles found in the cache, in text order
     */
    public List<Role> getRoles() { return roles; }
    /**
     * Returns the mentioned channels found in the cache, in text order.
     *
     * @return the mentioned channels found in the cache, in text order
     */
    public List<GuildChannel> getChannels() { return channels; }
    /**
     * Returns whether the message mentions everyone or here.
     *
     * @return whether the message mentions everyone or here
     */
    public boolean mentionsEveryone() { return everyone; }
    /**
     * Returns whether a user is mentioned.
     *
     * @param user the user
     * @return {@code true} if mentioned
     */
    public boolean isMentioned(User user) { return users.stream().anyMatch(value -> value.id().equals(user.id())); }
    /**
     * Returns whether a member is mentioned.
     *
     * @param member the member
     * @return {@code true} if mentioned
     */
    public boolean isMentioned(Member member) { return isMentioned(member.user()); }
    /**
     * Returns whether a role is actively mentioned.
     *
     * @param role the role
     * @return {@code true} if mentioned
     */
    public boolean isMentioned(Role role) { return activeRoleIds.contains(role.id()); }
    /**
     * Retrieves the mentioned users' memberships over REST, one request per user. Never called by the getters.
     *
     * @return an action completing with the members in text order; empty outside a guild
     * @throws IllegalStateException if more than 100 users are mentioned
     */
    public RestAction<List<Member>> retrieveMembers() {
        if (guildId == null) return new RestAction<>(() -> java.util.concurrent.CompletableFuture.completedFuture(List.of()));
        if (users.size() > 100) throw new IllegalStateException("Mention member retrieval is limited to 100 users");
        RestAction<List<Member>> result = new RestAction<>(() -> java.util.concurrent.CompletableFuture.completedFuture(List.of()));
        for (var user : users) {
            result = result.flatMap(previous -> client.retrieveMemberById(guildId, user.id()).map(member -> {
                var next = new ArrayList<>(previous); next.add(member); return List.copyOf(next);
            }));
        }
        return result;
    }
    private static <T> List<T> resolve(List<String> ids, Function<String, T> resolver) {
        var result = new ArrayList<T>();
        for (String id : ids) { T value = resolver.apply(id); if (value != null) result.add(value); }
        return List.copyOf(result);
    }
    private static <T> List<T> ordered(LinkedHashMap<String, T> active, String content, String kind) {
        var result = new LinkedHashMap<String, T>();
        var matcher = TOKENS.matcher(content);
        while (matcher.find()) {
            String token = matcher.group(1), id = matcher.group(2);
            if ((token.equals(kind) || (kind.equals("@") && token.equals("@!"))) && active.containsKey(id))
                result.putIfAbsent(id, active.get(id));
        }
        active.forEach(result::putIfAbsent);
        return List.copyOf(result.values());
    }
}
