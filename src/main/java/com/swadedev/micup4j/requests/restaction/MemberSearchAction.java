package com.swadedev.micup4j.requests.restaction;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swadedev.micup4j.entities.ISnowflake;
import com.swadedev.micup4j.entities.MemberSearchResult;
import com.swadedev.micup4j.internal.Json;
import com.swadedev.micup4j.requests.RestAction;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Fluent indexed member search, a Micup extension with no JDA equivalent. Requires one of {@code MANAGE_GUILD},
 * {@code MANAGE_ROLES}, {@code MANAGE_NICKNAMES}, {@code BAN_MEMBERS}, {@code MODERATE_MEMBERS} or {@code KICK_MEMBERS}.
 * Join-source and invite-code filters are applied only for callers holding {@code MANAGE_GUILD}.
 */
public final class MemberSearchAction extends RestAction<MemberSearchResult> {
    /** The result order. */
    public enum SortField {
        /** By join time; the default, newest first. */
        JOINED_AT("joinedAt"),
        /** By text relevance; ignores the sort direction. */
        RELEVANCE("relevance");
        private final String value;
        SortField(String value) { this.value = value; }
    }

    private final ObjectNode body;

    /**
     * Creates the action.
     *
     * @param body    the mutable payload, shared with the factory
     * @param factory sends a copy of the payload with the effective page size
     */
    public MemberSearchAction(ObjectNode body, BiFunction<ObjectNode, Integer, RestAction<MemberSearchResult>> factory) {
        super(() -> {
            ObjectNode copy;
            synchronized (body) { copy = body.deepCopy(); }
            return factory.apply(copy, copy.path("limit").asInt(25)).submit();
        });
        this.body = body;
    }

    /**
     * Matches username, discriminator, global name, nickname and user ID text.
     *
     * @param query at most 100 characters; {@code null} or blank removes the text clause
     * @return this action
     * @throws IllegalArgumentException if the query is too long
     */
    public MemberSearchAction setQuery(String query) {
        if (query != null && query.strip().codePointCount(0, query.strip().length()) > 100)
            throw new IllegalArgumentException("Search query exceeds 100 characters");
        return put("query", query == null || query.isBlank() ? null : query.strip());
    }

    /**
     * Sets the page size.
     *
     * @param limit 1 to 100; the default is 25
     * @return this action
     */
    public MemberSearchAction setLimit(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Search limit must be 1-100");
        synchronized (body) { body.put("limit", limit); }
        return this;
    }

    /**
     * Skips matches. Some deployments return empty pages beyond 10,000 results.
     *
     * @param offset at least 0
     * @return this action
     */
    public MemberSearchAction setOffset(int offset) {
        if (offset < 0) throw new IllegalArgumentException("Search offset must not be negative");
        synchronized (body) { body.put("offset", offset); }
        return this;
    }

    /**
     * Requires every listed role.
     *
     * @param roles at most 10 roles; none removes the filter
     * @return this action
     */
    public MemberSearchAction setRoles(ISnowflake... roles) { return setRoleIds(Arrays.stream(roles).map(ISnowflake::getId).toList()); }

    /**
     * Requires every listed role.
     *
     * @param roleIds at most 10 role IDs; empty removes the filter
     * @return this action
     */
    public MemberSearchAction setRoleIds(Collection<String> roleIds) { return array("role_ids", List.copyOf(roleIds).stream().map(Json::id).toList()); }

    /**
     * Limits the join time; both ends are inclusive and second precision.
     *
     * @param after the earliest join time, or {@code null} for no lower bound
     * @param before the latest join time, or {@code null} for no upper bound
     * @return this action
     */
    public MemberSearchAction setJoinedBetween(Instant after, Instant before) { return range("joined_at", after, before); }

    /**
     * Limits the account creation time; both ends are inclusive and second precision.
     *
     * @param after the earliest creation time, or {@code null}
     * @param before the latest creation time, or {@code null}
     * @return this action
     */
    public MemberSearchAction setUserCreatedBetween(Instant after, Instant before) { return range("user_created_at", after, before); }

    /**
     * Filters by account type.
     *
     * @param bot {@code true} for bots only, {@code false} for non-bots only, {@code null} for both
     * @return this action
     */
    public MemberSearchAction setBot(Boolean bot) {
        synchronized (body) { if (bot == null) body.remove("is_bot"); else body.put("is_bot", bot); }
        return this;
    }

    /**
     * Includes the listed join source types (see Micup's join source table).
     *
     * @param types at most 10 values; none removes the filter
     * @return this action
     */
    public MemberSearchAction setJoinSourceTypes(int... types) {
        if (Arrays.stream(types).anyMatch(type -> type < 0)) throw new IllegalArgumentException("Join source types must not be negative");
        return array("join_source_type", Arrays.stream(types).boxed().toList());
    }

    /**
     * Includes members who joined through the listed invites.
     *
     * @param codes at most 10 invite codes; none removes the filter
     * @return this action
     */
    public MemberSearchAction setSourceInviteCodes(String... codes) {
        if (Arrays.stream(codes).anyMatch(code -> code == null || code.isBlank())) throw new IllegalArgumentException("Invite codes must not be blank");
        return array("source_invite_code", List.of(codes));
    }

    /**
     * Sets the order.
     *
     * @param field the sort field
     * @param ascending {@code true} for ascending; ignored for relevance
     * @return this action
     */
    public MemberSearchAction setSort(SortField field, boolean ascending) {
        synchronized (body) { body.put("sort_by", field.value).put("sort_order", ascending ? "asc" : "desc"); }
        return this;
    }

    private MemberSearchAction put(String field, String value) {
        synchronized (body) { if (value == null) body.remove(field); else body.put(field, value); }
        return this;
    }

    private MemberSearchAction array(String field, List<?> values) {
        if (values.size() > 10) throw new IllegalArgumentException("At most 10 values are allowed for " + field);
        synchronized (body) {
            if (values.isEmpty()) { body.remove(field); return this; }
            ArrayNode array = body.putArray(field);
            values.forEach(value -> { if (value instanceof Integer number) array.add(number); else array.add(value.toString()); });
        }
        return this;
    }

    private MemberSearchAction range(String field, Instant after, Instant before) {
        if (after != null && before != null && after.isAfter(before)) throw new IllegalArgumentException("Range start is after its end");
        synchronized (body) {
            if (after == null) body.remove(field + "_gte"); else body.put(field + "_gte", after.getEpochSecond());
            if (before == null) body.remove(field + "_lte"); else body.put(field + "_lte", before.getEpochSecond());
        }
        return this;
    }
}
