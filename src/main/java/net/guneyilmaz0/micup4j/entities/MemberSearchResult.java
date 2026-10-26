package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.internal.Json;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One page of Micup's indexed member search. Results can lag membership changes and may include members who have left;
 * retrieve the member before acting on current state.
 *
 * @param guildId          the searched guild
 * @param members          the matched page in the requested order
 * @param totalResultCount the indexed match count; an estimate capped at 10,000 on some deployments
 * @param indexing         whether the index is still being built, in which case the page is empty; retry later
 */
public record MemberSearchResult(String guildId, List<Entry> members, int totalResultCount, boolean indexing) {
    /**
     * Validates the guild id and copies the page.
     *
     * @throws IllegalArgumentException if the guild id is invalid
     */
    public MemberSearchResult { Json.id(guildId); members = List.copyOf(members); }

    /**
     * Returns whether this page is empty. An empty page without {@link #indexing()} can also mean search is unavailable.
     *
     * @return {@code true} when no member matched on this page
     */
    public boolean isEmpty() { return members.isEmpty(); }

    /**
     * A partial membership. It has no avatar, timeout or voice state.
     *
     * @param guildId          the guild
     * @param userId           the account
     * @param username         the username
     * @param discriminator    the four-digit discriminator
     * @param globalName       the global display name, or {@code null}
     * @param nickname         the guild nickname, or {@code null}
     * @param roleIds          assigned role IDs, never including the everyone role
     * @param joinedAt         when the account joined, second precision
     * @param bot              whether the account is a bot
     * @param joinSourceType   the recorded join source, or {@code null} when unknown or the caller lacks {@code MANAGE_GUILD}
     * @param sourceInviteCode the invite code used, or {@code null}
     * @param inviterId        the inviter, or {@code null}
     */
    public record Entry(String guildId, String userId, String username, String discriminator, String globalName,
                        String nickname, List<String> roleIds, Instant joinedAt, boolean bot, Integer joinSourceType,
                        String sourceInviteCode, String inviterId) implements ISnowflake {
        /**
         * Validates the ids and copies the role list.
         *
         * @throws IllegalArgumentException if an id is invalid
         */
        public Entry { Json.id(guildId); Json.id(userId); roleIds = List.copyOf(roleIds); }
        @Override public String getId() { return userId; }
        /**
         * Returns the nickname, else the global name, else the username.
         *
         * @return the nickname, else the global name, else the username
         */
        public String getEffectiveName() { return nickname != null ? nickname : globalName != null ? globalName : username; }
    }

    /**
     * Parses a search response.
     *
     * @param data the response envelope
     * @param limit the requested page size
     * @return the result
     * @throws IllegalStateException if the response is malformed or exceeds the requested limit
     */
    public static MemberSearchResult fromJson(JsonNode data, int limit) {
        JsonNode page = data.path("members");
        if (!data.isObject() || !page.isArray() || page.size() > limit) throw new IllegalStateException("Invalid member search response");
        String guildId = data.path("guild_id").asText();
        var entries = new ArrayList<Entry>();
        for (JsonNode node : page) {
            var roles = new ArrayList<String>();
            for (JsonNode role : node.path("role_ids")) roles.add(Json.id(role.asText()));
            JsonNode extra = node.path("supplemental");
            entries.add(new Entry(node.path("guild_id").asText(guildId), node.path("user_id").asText(), node.path("username").asText(),
                    node.path("discriminator").asText(), text(node, "global_name"), text(node, "nickname"), roles,
                    Instant.ofEpochSecond(node.path("joined_at").asLong()), node.path("is_bot").asBoolean(),
                    extra.hasNonNull("join_source_type") ? extra.path("join_source_type").asInt() : null,
                    text(extra, "source_invite_code"), extra.hasNonNull("inviter_id") ? Json.id(extra.path("inviter_id").asText()) : null));
        }
        return new MemberSearchResult(guildId, entries, data.path("total_result_count").asInt(), data.path("indexing").asBoolean());
    }

    private static String text(JsonNode node, String field) { return node.hasNonNull(field) ? node.path(field).asText() : null; }
}
