package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.requests.RestAction;
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
    public Micup getJDA() { return client; }
    public List<User> getUsers() { return users; }
    public List<Member> getMembers() { return members; }
    public List<Role> getRoles() { return roles; }
    public List<GuildChannel> getChannels() { return channels; }
    public boolean mentionsEveryone() { return everyone; }
    public boolean isMentioned(User user) { return users.stream().anyMatch(value -> value.id().equals(user.id())); }
    public boolean isMentioned(Member member) { return isMentioned(member.user()); }
    public boolean isMentioned(Role role) { return activeRoleIds.contains(role.id()); }
    /** Explicit lazy HTTP lookup for at most 100 active user mentions; never called by the getters. */
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
