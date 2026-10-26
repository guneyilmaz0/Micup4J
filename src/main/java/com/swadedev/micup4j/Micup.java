package com.swadedev.micup4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.swadedev.micup4j.entities.*;
import com.swadedev.micup4j.events.*;
import com.swadedev.micup4j.hooks.EventListener;
import com.swadedev.micup4j.interactions.SlashCommand;
import com.swadedev.micup4j.internal.*;
import com.swadedev.micup4j.requests.RestAction;
import com.swadedev.micup4j.requests.restaction.AuditableRestAction;
import com.swadedev.micup4j.requests.restaction.RoleAction;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.concurrent.atomic.LongAdder;
import com.swadedev.micup4j.config.*;
import com.swadedev.micup4j.diagnostics.ClientMetrics;

/**
 * A single bot session. Owns its HTTP connections, event queue and Gateway lifecycle.
 *
 * <p>Build one with {@link com.swadedev.micup4j.MicupBuilder}; it is the Micup counterpart of JDA's {@code JDA}.
 *
 * <pre>{@code
 * Micup micup = MicupBuilder.createDefault(System.getenv("MICUP_TOKEN"))
 *         .addEventListeners(new MyListener())
 *         .build();
 * micup.awaitReady();
 * }</pre>
 *
 * <p>Threading: listeners run one event at a time on a dedicated daemon event thread. Gateway and REST callbacks never
 * run user code on their schedulers. Cached entities such as {@link #getGuildById(String)} are immutable snapshots that
 * are replaced, not mutated, when a Gateway event updates the cache.
 */
public final class Micup implements AutoCloseable {
    /**
     * Lifecycle states of a session.
     *
     * <p>{@link #SHUTTING_DOWN} is entered by {@link Micup#shutdown()} and {@link Micup#shutdownNow()} and always
     * ends in {@link #SHUTDOWN}; no other transition is accepted while shutting down.
     */
    public enum Status {
        /** The client was built and is discovering the instance. */
        INITIALIZING,
        /** A Gateway connection is being opened. */
        CONNECTING,
        /** The Gateway connection is open and the session is authenticating. */
        IDENTIFYING,
        /** The session is authenticated and receiving events. */
        CONNECTED,
        /** The Gateway connection was lost and is being resumed or re-identified. */
        RECONNECTING,
        /** The client failed permanently, for example because the token was rejected. */
        FAILED,
        /** A shutdown was requested; the Gateway is closed and pending REST requests are finishing. */
        SHUTTING_DOWN,
        /** The client has released all of its resources. Terminal. */
        SHUTDOWN
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final RestClient rest;
    private final EntityCache entityCache;
    private final GuildLifecycle guildLifecycle = new GuildLifecycle();
    private final com.swadedev.micup4j.managers.Presence presence;
    private final CopyOnWriteArrayList<EventListener> listeners;
    private final ThreadPoolExecutor events;
    private final Consumer<Throwable> errorHandler;
    private final int eventCapacity;
    private final LongAdder deliveredEvents = new LongAdder(), listenerFailures = new LongAdder();
    private final Set<CompletableFuture<Void>> pendingEvents = ConcurrentHashMap.newKeySet();
    private volatile long lastEventQueueDelay;
    private final CompletableFuture<Micup> ready = new CompletableFuture<>();
    private final CompletableFuture<Void> terminated = new CompletableFuture<>();
    /** Upper bound for the fallback shutdown thread to wait for the event thread before delivering the final events. */
    private static final Duration FINAL_EVENTS_WAIT = Duration.ofSeconds(5);
    /** Guards short status transitions only; the Gateway scheduler takes it, so never do payload-sized work here. */
    private final Object lifecycle = new Object();
    /**
     * Serializes entity cache and guild lifecycle writes with their teardown. Only the event thread blocks on it;
     * terminal paths use {@link #clearEntityState()}, which never waits for an in-progress apply.
     */
    private final java.util.concurrent.locks.ReentrantLock cacheLock = new java.util.concurrent.locks.ReentrantLock();
    /** Test seam run while an entity update holds {@link #cacheLock}; always {@code null} in production. */
    volatile Runnable cacheApplyHook;
    private volatile Status status = Status.INITIALIZING;
    private volatile User selfUser;
    private volatile String applicationId;
    private volatile GatewayClient gateway;
    private volatile URI mediaBase;
    private volatile boolean closed;
    /** Set once by {@link #shutdown()} or {@link #shutdownNow()}; guarded by {@link #lifecycle} for writes. */
    private volatile boolean shutdownRequested;
    private final Duration shutdownTimeout;
    /** False when the REST client is shared with other sessions; its owner then drains and closes it. */
    private final boolean ownsRest;
    private final int shardId, shardCount;
    private final CompletableFuture<HttpResponse<String>> discovery;

    Micup(String token, URI origin, List<EventListener> listeners, List<String> ignored, int restCapacity, int eventCapacity, int shardId, int shardCount, Consumer<Throwable> handler, RestConfig restConfig, GatewayConfig gatewayConfig, CachePolicy cachePolicy, com.swadedev.micup4j.managers.Presence.State initialPresence, RestClient sharedRest) {
        this.listeners = new CopyOnWriteArrayList<>(listeners);
        entityCache = new EntityCache(cachePolicy, this);
        presence = new com.swadedev.micup4j.managers.Presence(this, initialPresence);
        errorHandler = handler;
        this.eventCapacity = eventCapacity;
        events = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(eventCapacity),
                Thread.ofPlatform().daemon().name("micup4j-events").factory(), new ThreadPoolExecutor.AbortPolicy());
        ownsRest = sharedRest == null;
        rest = ownsRest ? new RestClient(http, token, restCapacity, restConfig) : sharedRest;
        this.shardId = shardId; this.shardCount = shardCount;
        shutdownTimeout = restConfig.shutdownTimeout();
        URI url = origin.resolve("/.well-known/fluxer");
        discovery = http.sendAsync(HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(20)).header("Accept", "application/json").header("User-Agent", com.swadedev.micup4j.MicupVersion.getUserAgent()).GET().build(), HttpResponse.BodyHandlers.ofString());
        discovery.whenComplete((response, failure) -> {
            synchronized (lifecycle) {
                if (closed || shutdownRequested) return;
                if (failure != null) {
                    gatewayFailed(new IllegalStateException("Instance discovery failed"));
                    return;
                }
                try {
                    if (response.statusCode() != 200)
                        throw new IllegalStateException("Instance discovery HTTP " + response.statusCode());
                    JsonNode endpoints = Json.parse(response.body()).path("endpoints");
                    rest.setBase(URI.create(endpoints.path("api_public").asText()));
                    if (endpoints.path("media").isTextual()) {
                        URI media = URI.create(endpoints.path("media").asText());
                        RestClient.requireEndpoint(media, false);
                        mediaBase = media;
                    }
                    gateway = new GatewayClient(this, http, URI.create(endpoints.path("gateway").asText()), token, ignored, shardId, shardCount, gatewayConfig);
                    gateway.start();
                } catch (Exception e) {
                    gatewayFailed(new IllegalStateException("Invalid instance discovery response"));
                }
            }
        });
    }

    /**
     * Returns this session's shard, as JDA's {@code getShardInfo()}.
     *
     * @return the shard id and count; {@code [0, 1]} for an unsharded session
     */
    public ShardInfo getShardInfo() { return new ShardInfo(shardId, Math.max(1, shardCount)); }

    /**
     * A session's shard assignment.
     *
     * @param shardId    the zero-based shard id
     * @param shardTotal the total number of shards
     */
    public record ShardInfo(int shardId, int shardTotal) {
        /**
         * Returns the shard id, as JDA's {@code getShardId()}.
         *
         * @return the zero-based shard id
         */
        public int getShardId() { return shardId; }
        /**
         * Returns the total shard count, as JDA's {@code getShardTotal()}.
         *
         * @return the total shard count, at least 1
         */
        public int getShardTotal() { return shardTotal; }
        /**
         * Formats the shard as JDA does.
         *
         * @return {@code [id / total]}
         */
        public String getShardString() { return "[" + shardId + " / " + shardTotal + "]"; }
    }

    /**
     * Returns whether this session shares its REST client with other sessions.
     *
     * @return {@code true} when the client was supplied through {@link MicupBuilder#setRestClient(RestClient)}
     */
    public boolean isRestClientShared() { return !ownsRest; }

    /**
     * Returns the presence manager of this session.
     *
     * @return the presence manager
     */
    public com.swadedev.micup4j.managers.Presence getPresence() { return presence; }
    /** Signals one replaceable desired presence, not an unbounded stream of Gateway commands. */
    public void presenceChanged() { var connection = gateway; if (connection != null) connection.updatePresence(); }

    /**
     * Returns the current lifecycle state. Safe to call from any thread.
     *
     * @return the status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Returns the bot's own user.
     *
     * @return the self user
     * @throws IllegalStateException if the client is not ready yet
     */
    public User getSelfUser() {
        if (selfUser == null) throw new IllegalStateException("Client is not ready");
        return selfUser;
    }

    /**
     * Returns the id of the bot's application, which is used for command and interaction routes.
     *
     * @return the application id
     * @throws IllegalStateException if called before the Gateway {@code READY} dispatch
     */
    public String getApplicationId() {
        if (applicationId == null) throw new IllegalStateException("Application ID is unavailable before Ready");
        return applicationId;
    }

    /**
     * Returns the REST client of this session. Internal infrastructure that is exposed for building custom requests; prefer the typed methods.
     *
     * @return the REST client
     */
    public RestClient getRestClient() {
        return rest;
    }

    /**
     * Returns the instance's discovered Media Proxy base URL, including any deployment path prefix.
     *
     * @return the media base URL
     * @throws IllegalStateException if discovery published no media endpoint
     */
    public URI getMediaBase() {
        if (mediaBase == null) throw new IllegalStateException("Instance discovery did not publish a media endpoint");
        return mediaBase;
    }

    /**
     * Returns the latest Gateway heartbeat round-trip time.
     *
     * @return the ping in milliseconds, or {@code -1} while no Gateway connection exists
     */
    public long getGatewayPing() {
        return gateway == null ? -1 : gateway.getPing();
    }

    /**
     * Returns a future that completes when the session is ready.
     *
     * <p>Every call returns an independent copy, so cancelling it does not affect other callers. It fails exceptionally
     * if the session fails permanently or is shut down before becoming ready.
     *
     * @return a future completing with this session
     */
    public CompletableFuture<Micup> ready() {
        return ready.copy();
    }

    /**
     * Returns a future that completes on shutdown, or exceptionally when the client fails permanently.
     *
     * <p>Every call returns an independent copy. The future may complete before listeners have received the final
     * {@link StatusChangeEvent} and the {@link com.swadedev.micup4j.events.session.ShutdownEvent}: termination
     * deliberately does not wait for the event thread, so a listener waiting on it cannot deadlock the shutdown.
     *
     * @return a future completing when the client has terminated
     */
    public CompletableFuture<Void> termination() {
        return terminated.copy();
    }

    /**
     * Blocks until the session is ready. Equivalent to JDA's {@code JDA#awaitReady()}.
     *
     * @return this session
     * @throws InterruptedException if the waiting thread is interrupted
     * @throws IllegalStateException if the session failed or was shut down before becoming ready; the cause is attached
     */
    public Micup awaitReady() throws InterruptedException {
        try { return ready.get(); }
        catch (ExecutionException error) { throw new IllegalStateException("Micup failed before Ready", error.getCause()); }
    }

    /**
     * Blocks until the session is ready or the timeout elapses.
     *
     * @param timeout the maximum time to wait
     * @return this session
     * @throws InterruptedException if the waiting thread is interrupted
     * @throws ExecutionException   if the session failed or was shut down before becoming ready
     * @throws TimeoutException     if the timeout elapsed first
     */
    public Micup awaitReady(Duration timeout) throws InterruptedException, ExecutionException, TimeoutException {
        return ready.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Registers a listener. It receives events queued after this call. Safe to call from any thread, including a listener.
     *
     * @param listener the listener
     * @return this session
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    public Micup addEventListener(EventListener listener) {
        listeners.add(Objects.requireNonNull(listener));
        return this;
    }

    /**
     * Unregisters a listener; unknown listeners are ignored. Safe to call from any thread.
     *
     * @param listener the listener
     * @return this session
     */
    public Micup removeEventListener(EventListener listener) {
        listeners.remove(listener);
        return this;
    }

    /**
     * Returns a lightweight channel handle. Creating it performs no request and does not verify that the channel exists.
     *
     * @param id the channel id
     * @return the channel handle
     */
    public MessageChannel getMessageChannelById(String id) {
        return new MessageChannel(this, id);
    }

    /**
     * Returns a cached text channel. Uncached and non-text channel ids return {@code null}.
     *
     * @param id the channel id
     * @return the text channel, or {@code null}
     */
    public com.swadedev.micup4j.entities.channel.concrete.TextChannel getTextChannelById(String id) {
        GuildChannel channel = entityCache.getChannel(Json.id(id)).orElse(null);
        return channel == null || channel.rawType() != 0 ? null
                : new com.swadedev.micup4j.entities.channel.concrete.TextChannel(this, id, channel.guildId(), channel.name());
    }
    /**
     * Returns a cached text channel. Uncached and non-text channel ids return {@code null}.
     *
     * @param id the channel id as an unsigned 64-bit value
     * @return the text channel, or {@code null}
     */
    public com.swadedev.micup4j.entities.channel.concrete.TextChannel getTextChannelById(long id) {
        return getTextChannelById(Long.toUnsignedString(id));
    }

    /**
     * Retrieves a user over REST.
     *
     * @param id the user id
     * @return an action that fetches the user when queued
     * @throws IllegalArgumentException if {@code id} is not a canonical snowflake
     */
    public RestAction<User> retrieveUserById(String id) {
        return rest.request("GET", "/users/" + Json.id(id), null).map(data -> User.fromJson(data).withClient(this));
    }

    /**
     * Returns a cached guild snapshot. A miss means uncached, not necessarily inaccessible or nonexistent.
     *
     * @param id the guild id
     * @return the cached snapshot, or {@code null}
     */
    public Guild getGuildById(String id) { return findGuildById(id).orElse(null); }
    /**
     * Returns the cached guild snapshot; see {@link #getGuildById(String)}.
     *
     * @param id the guild id as an unsigned 64-bit value
     * @return the cached snapshot, or {@code null}
     */
    public Guild getGuildById(long id) { return getGuildById(Long.toUnsignedString(id)); }
    /**
     * Returns the stored client-bound snapshot; repeated lookups without intervening writes return the same instance.
     *
     * @param id the guild id
     * @return the cached snapshot, or empty when uncached
     */
    public Optional<Guild> findGuildById(String id) { return entityCache.getGuild(Json.id(id)); }
    /**
     * Returns the cached guild, or a lightweight id reference for REST operations when it is not cached.
     *
     * @param id the guild id
     * @return a cached snapshot or an empty reference bound to this client
     */
    public Guild getGuildReferenceById(String id) {
        Guild guild = getGuildById(id);
        return guild == null ? new Guild(Json.id(id), "", null, false, List.of(), List.of(), List.of(), false, false).withClient(this) : guild;
    }

    /**
     * Returns a snapshot of all cached guilds. Empty when guild caching is disabled by the {@link com.swadedev.micup4j.config.CachePolicy}.
     *
     * @return an immutable list
     */
    public List<Guild> getGuilds() { return entityCache.getGuilds(); }

    /**
     * Retrieves a guild over REST. HTTP reads return detached snapshots and never race Gateway updates into the cache.
     *
     * @param id the guild id
     * @return an action that fetches the guild when queued
     */
    public RestAction<Guild> retrieveGuildById(String id) {
        return rest.request("GET", "/guilds/" + Json.id(id), null).map(data -> Guild.fromJson(data).withClient(this));
    }

    /**
     * Retrieves all roles of a guild over REST.
     *
     * @param guildId the guild id
     * @return an action that fetches the roles when queued
     * @throws IllegalArgumentException if {@code guildId} is not a canonical snowflake
     */
    public RestAction<List<Role>> retrieveRoles(String guildId) {
        String id = Json.id(guildId);
        return rest.request("GET", "/guilds/" + id + "/roles", null)
                .map(data -> readList(data, Integer.MAX_VALUE, value -> Role.fromJson(id, value)));
    }

    /**
     * Retrieves all channels of a guild over REST.
     *
     * @param guildId the guild id
     * @return an action that fetches the channels when queued
     * @throws IllegalArgumentException if {@code guildId} is not a canonical snowflake
     */
    public RestAction<List<GuildChannel>> retrieveGuildChannels(String guildId) {
        String id = Json.id(guildId);
        return rest.request("GET", "/guilds/" + id + "/channels", null)
                .map(data -> readList(data, Integer.MAX_VALUE, value -> GuildChannel.fromJson(id, value)));
    }

    /**
     * Retrieves one guild channel over REST.
     *
     * @param channelId the channel id
     * @return an action that fetches the channel when queued
     * @throws IllegalArgumentException if {@code channelId} is not a canonical snowflake
     */
    public RestAction<GuildChannel> retrieveGuildChannelById(String channelId) {
        return rest.request("GET", "/channels/" + Json.id(channelId), null)
                .map(data -> GuildChannel.fromJson(data.path("guild_id").asText(), data));
    }

    /**
     * Retrieves one guild member over REST.
     *
     * @param guildId the guild id
     * @param userId  the user id
     * @return an action that fetches the member when queued
     * @throws IllegalArgumentException if an id is not a canonical snowflake
     */
    public RestAction<Member> retrieveMemberById(String guildId, String userId) {
        String id = Json.id(guildId);
        return rest.request("GET", "/guilds/" + id + "/members/" + Json.id(userId), null)
                .map(data -> Member.fromJson(id, data).withClient(this));
    }

    /**
     * Retrieves the bot own membership of a guild over REST.
     *
     * @param guildId the guild id
     * @return an action that fetches the member when queued
     * @throws IllegalArgumentException if {@code guildId} is not a canonical snowflake
     */
    public RestAction<Member> retrieveSelfMember(String guildId) {
        String id = Json.id(guildId);
        return rest.request("GET", "/guilds/" + id + "/members/@me", null).map(data -> Member.fromJson(id, data).withClient(this));
    }

    /**
     * Retrieves one bounded page of members in ascending user-id order. Pass the last returned id as the next exclusive
     * cursor; an empty page ends the listing.
     *
     * @param guildId the guild id
     * @param limit   the page size, 1-1000
     * @param after   the exclusive user-id cursor, or {@code null} for the first page
     * @return an action that fetches the page when queued; it fails if the response is oversized or does not advance
     * @throws IllegalArgumentException if the limit or an id is invalid
     */
    public RestAction<List<Member>> retrieveMembers(String guildId, int limit, String after) {
        String id = Json.id(guildId);
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Member page limit must be 1-1000");
        String cursor = after == null ? "" : "&after=" + Json.id(after);
        return rest.request("GET", "/guilds/" + id + "/members?limit=" + limit + cursor, null).map(data -> {
            List<Member> members = readList(data, limit, value -> Member.fromJson(id, value).withClient(this));
            java.math.BigInteger previous = after == null ? java.math.BigInteger.ZERO : new java.math.BigInteger(after);
            for (Member member : members) {
                var next = new java.math.BigInteger(member.getId());
                if (next.compareTo(previous) <= 0) throw new IllegalStateException("Member page did not advance its cursor");
                previous = next;
            }
            return members;
        });
    }

    /**
     * Requests members whose display name (nickname, then global name, then username) starts with {@code prefix},
     * case-insensitively, over the Gateway. Results also update the member cache.
     *
     * @param guildId the guild id; the session must be connected to it
     * @param prefix  a non-empty prefix
     * @param limit   1 to 100
     * @return a running task; see {@link GatewayClient#requestMembers} for failure modes
     * @throws IllegalArgumentException if an argument is invalid
     */
    public com.swadedev.micup4j.utils.concurrent.Task<List<Member>> retrieveMembersByPrefix(String guildId, String prefix, int limit) {
        if (prefix == null || prefix.isEmpty()) throw new IllegalArgumentException("Prefix must not be empty");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Limit must be 1-100");
        return requestMembers(guildId, Json.object().put("query", prefix).put("limit", limit));
    }

    /**
     * Requests specific members over the Gateway. Users that are not members are absent from the result.
     *
     * @param guildId the guild id; the session must be connected to it
     * @param userIds 1 to 100 user ids
     * @return a running task
     * @throws IllegalArgumentException if an id is invalid or the count is outside 1-100
     */
    public com.swadedev.micup4j.utils.concurrent.Task<List<Member>> retrieveMembersByIds(String guildId, Collection<String> userIds) {
        var ids = new LinkedHashSet<String>();
        for (String userId : userIds) ids.add(Json.id(userId));
        if (ids.isEmpty() || ids.size() > 100) throw new IllegalArgumentException("Request 1-100 user ids");
        var data = Json.object();
        ids.forEach(data.putArray("user_ids")::add);
        return requestMembers(guildId, data);
    }

    /**
     * Requests specific members over the Gateway; see {@link #retrieveMembersByIds(String, Collection)}.
     *
     * @param guildId the guild id
     * @param userIds 1 to 100 user ids
     * @return a running task
     */
    public com.swadedev.micup4j.utils.concurrent.Task<List<Member>> retrieveMembersByIds(String guildId, String... userIds) {
        return retrieveMembersByIds(guildId, Arrays.asList(userIds));
    }

    /**
     * Requests the complete member list (up to 100,000 members) over the Gateway. Micup admits one such request per bot
     * and guild every 30 seconds and fails others with {@link com.swadedev.micup4j.requests.GatewayRateLimitException}.
     * The whole list is held in memory; the member cache still applies its {@link CachePolicy} bound.
     *
     * @param guildId the guild id; the session must be connected to it
     * @return a running task
     * @throws IllegalArgumentException if {@code guildId} is not a canonical snowflake
     */
    public com.swadedev.micup4j.utils.concurrent.Task<List<Member>> loadMembers(String guildId) {
        return requestMembers(guildId, Json.object().put("query", "").put("limit", 0));
    }

    private com.swadedev.micup4j.utils.concurrent.Task<List<Member>> requestMembers(String guildId, com.fasterxml.jackson.databind.node.ObjectNode data) {
        String id = Json.id(guildId);
        data.put("guild_id", id);
        var connection = gateway;
        CompletableFuture<List<JsonNode>> raw = connection == null
                ? CompletableFuture.failedFuture(new IllegalStateException("Gateway is not connected")) : connection.requestMembers(data);
        // Parse off the Gateway thread so callbacks never run on it.
        CompletableFuture<List<Member>> members = raw.thenApplyAsync(nodes ->
                nodes.stream().map(node -> Member.fromJson(id, node).withClient(this)).toList(), MEMBER_PARSER);
        members.whenComplete((value, error) -> { if (error != null) raw.cancel(false); });
        return new GatewayTask<>(members);
    }

    private static final Executor MEMBER_PARSER = task -> Thread.ofVirtual().name("micup4j-members").start(task);

    private InteractionCreateEvent interactionEvent(long sequence, JsonNode data) {
        return switch (data.path("type").asInt()) {
            case 2 -> new com.swadedev.micup4j.events.interaction.command.SlashCommandInteractionEvent(this, sequence, data);
            case 3 -> switch (data.path("data").path("component_type").asInt()) {
                case 2 -> new com.swadedev.micup4j.events.interaction.component.ButtonInteractionEvent(this, sequence, data);
                case 3 -> new com.swadedev.micup4j.events.interaction.component.StringSelectInteractionEvent(this, sequence, data);
                case 5, 6, 7, 8 -> new com.swadedev.micup4j.events.interaction.component.EntitySelectInteractionEvent(this, sequence, data);
                default -> new com.swadedev.micup4j.events.interaction.component.GenericComponentInteractionCreateEvent(this, sequence, data);
            };
            case 4 -> new com.swadedev.micup4j.events.interaction.command.CommandAutoCompleteInteractionEvent(this, sequence, data);
            case 5 -> new com.swadedev.micup4j.events.interaction.ModalInteractionEvent(this, sequence, data);
            default -> new InteractionCreateEvent(this, sequence, data);
        };
    }

    /**
     * Searches Micup's member index. Results are partial and may be stale; see {@link MemberSearchResult}.
     *
     * @param guildId the guild id
     * @return a fluent search action; nothing is sent until it is executed
     * @throws IllegalArgumentException if {@code guildId} is not a canonical snowflake
     */
    public com.swadedev.micup4j.requests.restaction.MemberSearchAction searchMembers(String guildId) {
        String path = "/guilds/" + Json.id(guildId) + "/members-search";
        return new com.swadedev.micup4j.requests.restaction.MemberSearchAction(Json.object(), (body, limit) ->
                rest.request("POST", path, body).map(data -> MemberSearchResult.fromJson(data, limit)));
    }

    /**
     * Renames a guild.
     *
     * @param guildId the guild id
     * @param name    the new name, 1 to 100 characters
     * @return an action that performs the change when queued and completes with the updated guild; supports an audit reason
     * @throws IllegalArgumentException if an id is not a canonical snowflake or the name is empty or too long
     */
    public AuditableRestAction<Guild> modifyGuildName(String guildId, String name) {
        var body = Json.object().put("name", boundedName(name, 100));
        return audited("PATCH", "/guilds/" + Json.id(guildId), body, data -> Guild.fromJson(data).withClient(this));
    }

    /**
     * Changes a member's nickname. The server enforces hierarchy and permissions.
     *
     * @param guildId  the guild id
     * @param userId   the member's user id
     * @param nickname the nickname, 1-32 characters, or {@code null} to clear it
     * @return an action that performs the change when queued; supports an audit reason
     * @throws IllegalArgumentException if an id is invalid or the nickname is too long
     */
    public AuditableRestAction<Member> modifyMemberNickname(String guildId, String userId, String nickname) {
        String id = Json.id(guildId);
        if (nickname != null) boundedName(nickname, 32);
        return audited("PATCH", "/guilds/" + id + "/members/" + Json.id(userId), Json.object().put("nick", nickname),
                data -> Member.fromJson(id, data).withClient(this));
    }

    /**
     * Adds a role to a member. The server enforces hierarchy and permissions.
     *
     * @param guildId the guild id
     * @param userId  the user id
     * @param roleId  the role id
     * @return an action that adds the role when queued; supports an audit reason
     * @throws IllegalArgumentException if an id is not a canonical snowflake
     */
    public AuditableRestAction<Void> addMemberRole(String guildId, String userId, String roleId) {
        return memberRole("PUT", guildId, userId, roleId);
    }

    /**
     * Removes a role from a member. The server enforces hierarchy and permissions.
     *
     * @param guildId the guild id
     * @param userId  the user id
     * @param roleId  the role id
     * @return an action that removes the role when queued; supports an audit reason
     * @throws IllegalArgumentException if an id is not a canonical snowflake
     */
    public AuditableRestAction<Void> removeMemberRole(String guildId, String userId, String roleId) {
        return memberRole("DELETE", guildId, userId, roleId);
    }

    private AuditableRestAction<Void> memberRole(String method, String guildId, String userId, String roleId) {
        return audited(method, "/guilds/" + Json.id(guildId) + "/members/" + Json.id(userId) + "/roles/" + Json.id(roleId), null, value -> null);
    }

    /**
     * Creates a role with Micup defaults. Configure it through the returned action before queueing it.
     *
     * @param guildId the guild id
     * @return a fluent creation action; supports an audit reason
     * @throws IllegalArgumentException if {@code guildId} is not a canonical snowflake
     */
    public RoleAction createRole(String guildId) {
        String id = Json.id(guildId);
        return new RoleAction(new RoleFields(), (fields, reason) ->
                rest.request("POST", "/guilds/" + id + "/roles", fields.body(), fields.headers(reason)).map(data -> Role.fromJson(id, data)));
    }

    /**
     * Creates a role with a name, color and optional permission mask. A supplied mask is stored exactly, including
     * feature-gated bits.
     *
     * @param guildId     the guild id
     * @param name        the name, 1-100 characters
     * @param color       a 24-bit RGB color
     * @param permissions the mask, or {@code null} to copy the everyone role
     * @return a creation action that can be configured further; supports an audit reason
     * @throws IllegalArgumentException if a value is invalid
     */
    public RoleAction createRole(String guildId, String name, int color,
                                 com.swadedev.micup4j.permissions.PermissionSet permissions) {
        if (color < 0 || color > 0xFFFFFF) throw new IllegalArgumentException("Color must be a 24-bit RGB value");
        return createRole(guildId).setName(name).setColor(color).setPermissions(permissions);
    }

    /**
     * Modifies a role. Only fields set on the returned manager are sent, and a supplied permission mask is stored exactly,
     * including feature-gated bits. Position changes are not part of this route.
     *
     * @param guildId the guild id
     * @param roleId  the role id
     * @return a fluent manager; supports an audit reason
     * @throws IllegalArgumentException if an id is not a canonical snowflake
     */
    public com.swadedev.micup4j.managers.RoleManager modifyRole(String guildId, String roleId) {
        String path = "/guilds/" + Json.id(guildId) + "/roles/" + Json.id(roleId);
        return new com.swadedev.micup4j.managers.RoleManager(new RoleFields(), (fields, reason) ->
                rest.request("PATCH", path, fields.body(), fields.headers(reason)).map(data -> (Void) null));
    }

    /**
     * Renames a role.
     *
     * @param guildId the guild id
     * @param roleId  the role id
     * @param name    the new name, 1 to 100 characters
     * @return an action that performs the change when queued and completes with the updated role; supports an audit reason
     * @throws IllegalArgumentException if an id is not a canonical snowflake or the name is empty or too long
     */
    public AuditableRestAction<Role> modifyRoleName(String guildId, String roleId, String name) {
        String id = Json.id(guildId);
        return audited("PATCH", "/guilds/" + id + "/roles/" + Json.id(roleId), Json.object().put("name", boundedName(name, 100)),
                data -> Role.fromJson(id, data));
    }

    /**
     * Deletes a role.
     *
     * @param guildId the guild id
     * @param roleId  the role id
     * @return an action that deletes the role when queued; supports an audit reason
     * @throws IllegalArgumentException if an id is not a canonical snowflake
     */
    public AuditableRestAction<Void> deleteRole(String guildId, String roleId) {
        return audited("DELETE", "/guilds/" + Json.id(guildId) + "/roles/" + Json.id(roleId), null, value -> null);
    }

    /**
     * Removes a member from a guild. Requires {@code KICK_MEMBERS} and hierarchy authority.
     *
     * @param guildId the guild id
     * @param userId  the member's user id
     * @return an action that kicks the member when queued; supports an audit reason
     * @throws IllegalArgumentException if an id is not a canonical snowflake
     */
    public AuditableRestAction<Void> kick(String guildId, String userId) {
        return audited("DELETE", "/guilds/" + Json.id(guildId) + "/members/" + Json.id(userId), null, value -> null);
    }

    /**
     * Bans a user permanently. Requires {@code BAN_MEMBERS}; banning a member also requires hierarchy authority. The reason
     * is sent in the request body and may contain any Unicode text.
     *
     * @param guildId           the guild id
     * @param userId            the user id; need not be a member
     * @param deletionTimeframe how far back to delete the user's messages, 0 for none
     * @param unit              the unit of {@code deletionTimeframe}
     * @return an action that bans the user when queued; supports a reason
     * @throws IllegalArgumentException if an id is invalid or the timeframe is negative or exceeds 7 days
     */
    public AuditableRestAction<Void> ban(String guildId, String userId, int deletionTimeframe, java.util.concurrent.TimeUnit unit) {
        return ban(guildId, userId, deletionTimeframe, unit, null);
    }

    /**
     * Bans a user, optionally for a limited time (a Micup extension). See
     * {@link #ban(String, String, int, java.util.concurrent.TimeUnit)}.
     *
     * @param guildId           the guild id
     * @param userId            the user id; need not be a member
     * @param deletionTimeframe how far back to delete the user's messages, 0 for none
     * @param unit              the unit of {@code deletionTimeframe}
     * @param duration          the ban length, 60 seconds to 730 days, or {@code null} for a permanent ban
     * @return an action that bans the user when queued; supports a reason
     * @throws IllegalArgumentException if an id, the timeframe or the duration is outside its range
     */
    public AuditableRestAction<Void> ban(String guildId, String userId, int deletionTimeframe, java.util.concurrent.TimeUnit unit,
                                         Duration duration) {
        String path = "/guilds/" + Json.id(guildId) + "/bans/" + Json.id(userId);
        long seconds = Objects.requireNonNull(unit, "unit").toSeconds(deletionTimeframe);
        if (deletionTimeframe < 0 || seconds > 604_800) throw new IllegalArgumentException("Message deletion timeframe must be 0-7 days");
        if (duration != null && (duration.getSeconds() < 60 || duration.getSeconds() > 63_072_000 || duration.getNano() != 0))
            throw new IllegalArgumentException("Ban duration must be whole seconds between 60 seconds and 730 days");
        return new AuditableRestAction<>(reason -> {
            var body = Json.object().put("delete_message_seconds", seconds);
            if (duration != null) body.put("ban_duration_seconds", duration.getSeconds());
            if (reason != null) body.put("reason", reason);
            return rest.request("PUT", path, body).map(value -> (Void) null);
        }, false);
    }

    /**
     * Removes a ban.
     *
     * @param guildId the guild id
     * @param userId  the banned user id
     * @return an action that removes the ban when queued; supports an audit reason
     * @throws IllegalArgumentException if an id is not a canonical snowflake
     */
    public AuditableRestAction<Void> unban(String guildId, String userId) {
        return audited("DELETE", "/guilds/" + Json.id(guildId) + "/bans/" + Json.id(userId), null, value -> null);
    }

    /**
     * Times a member out for a duration. Requires {@code MODERATE_MEMBERS} and hierarchy authority; administrators cannot be
     * timed out. The reason is sent in the request body and may contain any Unicode text.
     *
     * @param guildId  the guild id
     * @param userId   the member's user id
     * @param duration a positive duration of at most 365 days
     * @return an action that applies the timeout when queued; supports a reason
     * @throws IllegalArgumentException if an id is invalid or the duration is outside its range
     */
    public AuditableRestAction<Member> timeoutFor(String guildId, String userId, Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero() || duration.compareTo(Duration.ofDays(365)) > 0)
            throw new IllegalArgumentException("Timeout must be positive and at most 365 days");
        return timeout(guildId, userId, () -> Instant.now().plus(duration));
    }

    /**
     * Times a member out until an instant; see {@link #timeoutFor(String, String, Duration)}.
     *
     * @param guildId the guild id
     * @param userId  the member's user id
     * @param until   a future instant at most 365 days ahead
     * @return an action that applies the timeout when queued; supports a reason
     * @throws IllegalArgumentException if an id is invalid or {@code until} is outside its range when this method is called
     */
    public AuditableRestAction<Member> timeoutUntil(String guildId, String userId, Instant until) {
        var now = Instant.now();
        if (until == null || !until.isAfter(now) || until.isAfter(now.plus(Duration.ofDays(365))))
            throw new IllegalArgumentException("Timeout must end in the future and at most 365 days ahead");
        return timeout(guildId, userId, () -> until);
    }

    /**
     * Removes a member's timeout.
     *
     * @param guildId the guild id
     * @param userId  the member's user id
     * @return an action that clears the timeout when queued; supports an audit reason
     * @throws IllegalArgumentException if an id is not a canonical snowflake
     */
    public AuditableRestAction<Member> removeTimeout(String guildId, String userId) {
        String id = Json.id(guildId);
        return audited("PATCH", "/guilds/" + id + "/members/" + Json.id(userId), Json.object().putNull("communication_disabled_until"),
                data -> Member.fromJson(id, data).withClient(this));
    }

    private AuditableRestAction<Member> timeout(String guildId, String userId, java.util.function.Supplier<Instant> until) {
        String id = Json.id(guildId), path = "/guilds/" + id + "/members/" + Json.id(userId);
        return new AuditableRestAction<>(reason -> {
            var body = Json.object().put("communication_disabled_until", until.get().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString());
            if (reason != null) body.put("timeout_reason", reason);
            return rest.request("PATCH", path, body).map(data -> Member.fromJson(id, data).withClient(this));
        }, false);
    }

    private <T> AuditableRestAction<T> audited(String method, String path, JsonNode body, java.util.function.Function<JsonNode, T> mapper) {
        return new AuditableRestAction<>(reason -> rest.request(method, path, body, AuditableRestAction.reasonHeaders(reason)).map(mapper), true);
    }

    private static String boundedName(String value, int maximum) {
        if (value == null || value.isEmpty() || value.codePointCount(0, value.length()) > maximum)
            throw new IllegalArgumentException("Name is outside its character limit");
        return value;
    }

    private static <T> List<T> readList(JsonNode data, int maximum, java.util.function.Function<JsonNode, T> parser) {
        if (!data.isArray() || data.size() > maximum) throw new IllegalStateException("Invalid entity collection response");
        var result = new ArrayList<T>();
        for (JsonNode entry : data) result.add(parser.apply(entry));
        return List.copyOf(result);
    }

    /**
     * Replaces all commands in the given guild with legacy definitions. Call only when command definitions change.
     *
     * @param guildId  the guild id
     * @param commands the complete new command set
     * @return the raw command list returned by Micup
     * @deprecated Use {@link #updateGuildCommands(String)} with {@code Commands.slash(...)}; kept through 1.x.
     */
    @Deprecated(since = "1.0.0")
    public RestAction<JsonNode> updateGuildCommands(String guildId, SlashCommand... commands) {
        var body = Json.MAPPER.createArrayNode();
        for (var command : commands) body.add(command.toJson());
        return rest.request("PUT", "/applications/" + Json.id(getApplicationId()) + "/guilds/" + Json.id(guildId) + "/commands", body);
    }

    /**
     * Replaces all global commands with legacy definitions.
     *
     * @param commands the complete new command set
     * @return the raw command list returned by Micup
     * @deprecated Use {@link #updateCommands()} with {@code Commands.slash(...)}; kept through 1.x.
     */
    @Deprecated(since = "1.0.0")
    public RestAction<JsonNode> updateCommands(SlashCommand... commands) {
        var body = Json.MAPPER.createArrayNode();
        for (var command : commands) body.add(command.toJson());
        return rest.request("PUT", "/applications/" + Json.id(getApplicationId()) + "/commands", body);
    }

    /**
     * Starts a bulk overwrite of the global commands. Equivalent to JDA's {@code JDA#updateCommands()}.
     *
     * @return the update action
     */
    public com.swadedev.micup4j.requests.restaction.CommandListUpdateAction updateCommands() {
        return new com.swadedev.micup4j.requests.restaction.CommandListUpdateAction(this, null);
    }
    /**
     * Starts a bulk overwrite of one guild's commands.
     *
     * @param guildId the guild id
     * @return the update action
     * @throws IllegalArgumentException if {@code guildId} is not a canonical snowflake
     */
    public com.swadedev.micup4j.requests.restaction.CommandListUpdateAction updateGuildCommands(String guildId) {
        return new com.swadedev.micup4j.requests.restaction.CommandListUpdateAction(this, Json.id(guildId));
    }
    /**
     * Returns the REST route of the command collection.
     *
     * @param guildId the guild id, or {@code null} for global commands
     * @return the route
     * @throws IllegalStateException if called before the Gateway {@code READY} dispatch
     * @throws IllegalArgumentException if {@code guildId} is not a canonical snowflake
     */
    public String commandPath(String guildId) {
        return "/applications/" + Json.id(getApplicationId()) + (guildId == null ? "" : "/guilds/" + Json.id(guildId)) + "/commands";
    }
    /**
     * Creates or updates a global command.
     *
     * @param command the command definition
     * @return an action that upserts the command when queued
     * @throws NullPointerException if {@code command} is {@code null}
     * @throws IllegalStateException if called before the Gateway {@code READY} dispatch
     */
    public RestAction<com.swadedev.micup4j.interactions.commands.Command> upsertCommand(
            com.swadedev.micup4j.interactions.commands.build.CommandData command) { return upsertGuildCommand(null, command); }
    /**
     * Creates or updates a command of one guild, or a global command when {@code guildId} is {@code null}.
     *
     * @param guildId the guild id, or {@code null} for a global command
     * @param command the command definition
     * @return an action that upserts the command when queued
     * @throws NullPointerException if {@code command} is {@code null}
     * @throws IllegalStateException if called before the Gateway {@code READY} dispatch
     * @throws IllegalArgumentException if {@code guildId} is not a canonical snowflake
     */
    public RestAction<com.swadedev.micup4j.interactions.commands.Command> upsertGuildCommand(String guildId,
            com.swadedev.micup4j.interactions.commands.build.CommandData command) {
        return rest.request("POST", commandPath(guildId), Objects.requireNonNull(command).toJson())
                .map(com.swadedev.micup4j.interactions.commands.Command::fromJson);
    }
    /**
     * Retrieves the global commands.
     *
     * @return an action that fetches the commands when queued
     * @throws IllegalStateException if called before the Gateway {@code READY} dispatch
     */
    public RestAction<List<com.swadedev.micup4j.interactions.commands.Command>> retrieveCommands() { return retrieveGuildCommands(null); }
    /**
     * Retrieves the commands of one guild, or the global commands when {@code guildId} is {@code null}.
     *
     * @param guildId the guild id, or {@code null} for global commands
     * @return an action that fetches the commands when queued
     * @throws IllegalStateException if called before the Gateway {@code READY} dispatch
     * @throws IllegalArgumentException if {@code guildId} is not a canonical snowflake
     */
    public RestAction<List<com.swadedev.micup4j.interactions.commands.Command>> retrieveGuildCommands(String guildId) {
        return rest.request("GET", commandPath(guildId), null)
                .map(data -> readList(data, Integer.MAX_VALUE, com.swadedev.micup4j.interactions.commands.Command::fromJson));
    }
    /**
     * Deletes a global command.
     *
     * @param commandId the command id
     * @return an action that deletes the command when queued
     * @throws IllegalStateException if called before the Gateway {@code READY} dispatch
     * @throws IllegalArgumentException if {@code commandId} is not a canonical snowflake
     */
    public RestAction<Void> deleteCommandById(String commandId) { return deleteGuildCommandById(null, commandId); }
    /**
     * Deletes a global command.
     *
     * @param commandId the command id as an unsigned 64-bit value
     * @return an action that deletes the command when queued
     * @throws IllegalStateException if called before the Gateway {@code READY} dispatch
     */
    public RestAction<Void> deleteCommandById(long commandId) { return deleteCommandById(Long.toUnsignedString(commandId)); }
    /**
     * Deletes a command of one guild, or a global command when {@code guildId} is {@code null}.
     *
     * @param guildId   the guild id, or {@code null} for a global command
     * @param commandId the command id
     * @return an action that deletes the command when queued
     * @throws IllegalStateException if called before the Gateway {@code READY} dispatch
     * @throws IllegalArgumentException if an id is not a canonical snowflake
     */
    public RestAction<Void> deleteGuildCommandById(String guildId, String commandId) {
        return rest.request("DELETE", commandPath(guildId) + "/" + Json.id(commandId), null).map(ignored -> null);
    }
    /**
     * Registers several listeners; see {@link #addEventListener(EventListener)}.
     *
     * @param values the listeners
     * @return this session
     * @throws NullPointerException if a listener is {@code null}
     */
    public Micup addEventListeners(com.swadedev.micup4j.hooks.EventListener... values) {
        for (var listener : values) addEventListener(listener); return this;
    }
    /**
     * Unregisters several listeners.
     *
     * @param values the listeners
     * @return this session
     */
    public Micup removeEventListeners(com.swadedev.micup4j.hooks.EventListener... values) {
        for (var listener : values) removeEventListener(listener); return this;
    }
    /**
     * Retrieves a user over REST.
     *
     * @param id the user id as an unsigned 64-bit value
     * @return an action that fetches the user when queued
     */
    public RestAction<User> retrieveUserById(long id) { return retrieveUserById(Long.toUnsignedString(id)); }
    /**
     * Returns a lightweight channel handle; see {@link #getMessageChannelById(String)}.
     *
     * @param id the channel id as an unsigned 64-bit value
     * @return the channel handle
     */
    public MessageChannel getMessageChannelById(long id) { return getMessageChannelById(Long.toUnsignedString(id)); }

    /**
     * Returns a point-in-time snapshot of the queue, delivery and REST counters. Contains no message content or credentials.
     *
     * @return the metrics snapshot
     */
    public ClientMetrics getMetrics() {
        return new ClientMetrics(status, rest.getMetrics(), events.getQueue().size(), eventCapacity,
                deliveredEvents.sum(), listenerFailures.sum(), lastEventQueueDelay, getGatewayPing(),
                gateway == null ? 0 : gateway.getReconnectCount());
    }

    /**
     * Moves the session to a new status and dispatches a {@link StatusChangeEvent}. Called by the library; not intended
     * for user code.
     *
     * <p>A change is ignored once the session is closed, when it would not change the status, and after {@link Status#FAILED}
     * or {@link Status#SHUTTING_DOWN} unless the target is {@link Status#SHUTDOWN}.
     *
     * @param next the new status
     */
    public void updateStatus(Status next) {
        synchronized (lifecycle) {
            StatusChangeEvent change = transition(next);
            if (change != null) dispatchDiagnostic(change);
        }
    }

    /**
     * Applies a status transition under {@link #lifecycle} without dispatching it.
     *
     * @return the status change to deliver, or {@code null} when the transition was ignored
     */
    private StatusChangeEvent transition(Status next) {
        if (closed || status == next || (status == Status.FAILED && next != Status.SHUTDOWN)
                || (status == Status.SHUTTING_DOWN && next != Status.SHUTDOWN)) return null;
        Status previous = status;
        status = next;
        return new StatusChangeEvent(this, previous, next);
    }

    /**
     * Handles the Gateway {@code READY} dispatch. Called by the Gateway client; not intended for user code.
     *
     * @param data the {@code READY} payload
     */
    public void onReady(JsonNode data) {
        synchronized (lifecycle) {
            if (closed || shutdownRequested || status == Status.FAILED) return;
            selfUser = User.fromJson(data.path("user")).withClient(this);
            applicationId = data.path("application").path("id").asText(selfUser.id());
            updateStatus(Status.CONNECTED);
            ready.complete(this);
        }
    }

    /**
     * Queues a local diagnostic event, best effort: it is dropped when the event queue is nearly full so protocol dispatches keep their slots. Not intended for user code.
     *
     * @param event the event
     */
    public void dispatchDiagnostic(GenericEvent event) {
        // Reserve a queue slot for protocol dispatches; local diagnostics are best-effort.
        if (events.getQueue().remainingCapacity() < 2) return;
        enqueueEvent(event, null);
    }

    /**
     * Converts a Gateway dispatch into a typed event and queues it for listeners. Called by the Gateway client; not
     * intended for user code.
     *
     * <p>A malformed payload of a typed event degrades to a plain {@link GenericEvent} and reports a failure without the
     * payload.
     *
     * @param type     the dispatch type
     * @param sequence the Gateway sequence number
     * @param data     the payload; must not be mutated afterwards
     * @return a future that completes once the listeners ran, or exceptionally when the event was dropped
     */
    public CompletableFuture<Void> dispatch(String type, long sequence, JsonNode data) {
        try {
            GenericEvent event = switch (type) {
                case "READY" -> typedOrRaw(type, sequence, data,
                        () -> new com.swadedev.micup4j.events.session.ReadyEvent(this, sequence, data));
                case "MESSAGE_CREATE" -> typedOrRaw(type, sequence, data,
                        () -> new com.swadedev.micup4j.events.message.MessageReceivedEvent(this, sequence, data));
                case "MESSAGE_UPDATE" -> typedOrRaw(type, sequence, data,
                        () -> new com.swadedev.micup4j.events.message.MessageUpdateEvent(this, sequence, data));
                case "MESSAGE_DELETE" -> typedOrRaw(type, sequence, data,
                        () -> new com.swadedev.micup4j.events.message.MessageDeleteEvent(this, sequence, data));
                // Member events stay raw here and are typed in applyEntityUpdate, around the cache apply.
                case "INTERACTION_CREATE" -> typedOrRaw(type, sequence, data, () -> interactionEvent(sequence, data));
                default -> new GenericEvent(this, type, sequence, data);
            };
            // The frame data is never mutated after parsing; the executor hand-off publishes it to the event thread.
            return enqueueEvent(event, data);
        } catch (Exception error) { return CompletableFuture.failedFuture(error); }
    }

    /**
     * Builds a typed event, degrading to a plain {@link GenericEvent} with the same type and sequence when the
     * payload is malformed. The failure is reported without its cause, so payload values such as interaction tokens
     * never reach the error handler, and the session keeps running.
     */
    private GenericEvent typedOrRaw(String type, long sequence, JsonNode data, Supplier<GenericEvent> typed) {
        try {
            return typed.get();
        } catch (RuntimeException invalid) {
            reportFailure(new IllegalStateException("Invalid " + type + " payload"));
            return new GenericEvent(this, type, sequence, data);
        }
    }

    /**
     * Queues an event for listeners.
     *
     * @param cacheData the Gateway frame data used for cache updates, or {@code null} for local diagnostics;
     *                  treated as read-only
     */
    private CompletableFuture<Void> enqueueEvent(GenericEvent event, JsonNode cacheData) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        long queuedAt = System.nanoTime();
        pendingEvents.add(completion);
        completion.whenComplete((value, error) -> pendingEvents.remove(completion));
        try {
            events.execute(() -> {
                lastEventQueueDelay = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - queuedAt);
                // After shutdown() no queued Gateway event starts. Local diagnostics, such as the SHUTTING_DOWN status
                // change, are still delivered until finish() queues the final SHUTDOWN events and shuts the executor
                // down; a hard close already removed queued diagnostics from the queue.
                if (cacheData != null && (closed || shutdownRequested)) {
                    completion.completeExceptionally(new IllegalStateException("Client closed"));
                    return;
                }
                GenericEvent delivered;
                try { delivered = applyEntityUpdate(event, cacheData); }
                catch (RuntimeException invalid) {
                    completion.completeExceptionally(new IllegalStateException("Invalid entity event: " + event.getType()));
                    return;
                }
                for (EventListener listener : listeners) {
                    try { listener.onEvent(delivered); }
                    catch (Throwable error) {
                        listenerFailures.increment();
                        reportFailure(new IllegalStateException("Listener failed for " + event.getType()));
                    }
                }
                deliveredEvents.increment();
                completion.complete(null);
            });
        } catch (Exception error) { completion.completeExceptionally(error); }
        return completion;
    }

    /**
     * Applies cache and guild lifecycle changes for one event on the event thread, before listeners see it.
     * Runs under {@link #cacheLock}, never under {@link #lifecycle}, so a large payload cannot stall the Gateway scheduler.
     *
     * @param data the read-only Gateway frame data, or {@code null} for local diagnostics (never cached)
     * @return the event to deliver, possibly replaced by a guild lifecycle event
     */
    private GenericEvent applyEntityUpdate(GenericEvent event, JsonNode data) {
        if (data == null) return event;
        boolean guildEvent = GuildLifecycle.accepts(event.getType());
        boolean memberEvent = MemberEvents.accepts(event.getType());
        if (!guildEvent && !memberEvent && !entityCache.accepts(event.getType())) return event;
        GenericEvent delivered = event;
        cacheLock.lock();
        try {
            if (terminated()) return event;
            var hook = cacheApplyHook;
            if (hook != null) hook.run();
            String guildId = data.path("guild_id").asText(data.path("id").asText(null));
            Guild before = guildEvent && guildId != null ? getGuildById(guildId)
                    : memberEvent ? cachedGuildOrNull(guildId) : null;
            entityCache.apply(event.getType(), data);
            if (guildEvent) {
                Guild after = guildId == null ? null : getGuildById(guildId);
                delivered = guildLifecycle.apply(this, event, data, before, after);
            } else if (memberEvent) {
                delivered = typedMemberEvent(event, data, before, cachedGuildOrNull(guildId));
            }
        } finally {
            cacheLock.unlock();
            // A terminal path that ran during the apply could not take the lock; finish its clear so nothing stale survives.
            if (terminated()) clearEntityState();
        }
        return delivered;
    }

    /**
     * Converts a member dispatch into its typed event. A malformed payload degrades to the raw event under the shared
     * fallback policy; the failure is reported without its cause so no payload value leaks.
     *
     * @param before the guild snapshot read before the cache apply, so a removed member is still present in it
     */
    private GenericEvent typedMemberEvent(GenericEvent event, JsonNode data, Guild before, Guild after) {
        try {
            return MemberEvents.apply(this, event, data, before, after);
        } catch (RuntimeException invalid) {
            reportFailure(new IllegalStateException("Invalid " + event.getType() + " payload"));
            return event;
        }
    }

    /** Cached guild for a possibly malformed id; {@code null} when the id is invalid or the guild is not cached. */
    private Guild cachedGuildOrNull(String guildId) {
        if (guildId == null) return null;
        try {
            return getGuildById(guildId);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private boolean terminated() {
        return closed || status == Status.FAILED;
    }

    /**
     * Drops cached entity state without waiting for an in-progress apply. Callers set {@code closed} or
     * {@code FAILED} first; if the lock is busy, its holder observes that flag after unlocking and clears instead.
     */
    private void clearEntityState() {
        // tryLock instead of lock: terminal paths run under lifecycle on the Gateway scheduler and must not wait for
        // an apply in progress. The apply's finally block re-checks terminated() after unlocking and clears the state itself.
        if (!cacheLock.tryLock()) return;
        try {
            entityCache.clear();
            guildLifecycle.clear();
        } finally {
            cacheLock.unlock();
        }
    }

    /**
     * Passes a failure to the error handler configured on the builder. A failing handler is logged without details and never propagates.
     *
     * @param error the failure; must not carry tokens or message content
     */
    public void reportFailure(Throwable error) {
        try {
            errorHandler.accept(error);
        } catch (Throwable ignored) {
            System.getLogger("Micup4J").log(System.Logger.Level.ERROR, "Error handler failed");
        }
    }

    /**
     * Fails the session permanently. Called by the Gateway client; not intended for user code. Ignored after a shutdown was requested.
     *
     * @param error the cause, also delivered to the error handler
     */
    public void gatewayFailed(Throwable error) {
        synchronized (lifecycle) {
            // A requested shutdown owns teardown; a late Gateway failure must not hard-cancel its REST drain.
            if (closed || shutdownRequested || status == Status.FAILED) return;
            updateStatus(Status.FAILED);
            ready.completeExceptionally(error);
            if (gateway != null) gateway.close();
            if (ownsRest) rest.close();
            events.shutdown();
            clearEntityState();
            http.shutdown();
            terminated.completeExceptionally(error);
        }
        reportFailure(error);
    }

    /**
     * Starts a graceful shutdown, like JDA's {@code JDA.shutdown()}.
     *
     * <p>The status becomes {@link Status#SHUTTING_DOWN}, the Gateway connection is closed, and queued Gateway
     * events that have not started are dropped; a listener that is currently running finishes normally. New REST
     * requests fail with {@link RejectedExecutionException}, while queued and in-flight requests, including rate-limit
     * retries, may finish for up to {@link RestConfig#shutdownTimeout()}. Whatever remains is then cancelled and the
     * status becomes {@link Status#SHUTDOWN}. Listeners then receive the final {@link StatusChangeEvent} and a
     * {@link com.swadedev.micup4j.events.session.ShutdownEvent} on the event thread, after every earlier event.
     *
     * <p>This method never blocks, is idempotent, and is safe to call from an event listener. Use
     * {@link #awaitShutdown(Duration)} to wait for completion, for example in a JVM shutdown hook.
     * Call {@link #shutdownNow()} to cancel pending requests instead.
     */
    public void shutdown() {
        CompletableFuture<Void> drain;
        synchronized (lifecycle) {
            if (closed || shutdownRequested) return;
            drain = beginShutdown();
        }
        // Finish on a fresh virtual thread: never on the REST scheduler, the Gateway scheduler or the caller.
        drain.whenCompleteAsync((ignored, error) -> finish(false),
                task -> Thread.ofVirtual().name("micup4j-shutdown").start(task));
    }

    /**
     * Shuts down immediately, like JDA's {@code JDA.shutdownNow()}.
     *
     * <p>Every queued and in-flight REST request is cancelled, queued events are dropped, and the status reaches
     * {@link Status#SHUTDOWN} before this method returns. It also escalates a graceful {@link #shutdown()} that is still
     * draining. Idempotent and non-blocking. The final {@link StatusChangeEvent} and the
     * {@link com.swadedev.micup4j.events.session.ShutdownEvent} are delivered asynchronously on a
     * {@code micup4j-shutdown} virtual thread, possibly after this method returns. That thread first waits up to five
     * seconds for a listener that is still running to return, so the final events normally arrive last and never
     * overlap another listener.
     *
     * <p>When called from an event listener, the event thread is interrupted, so the calling listener observes its
     * own interrupt flag after this method returns.
     */
    public void shutdownNow() {
        synchronized (lifecycle) {
            if (closed) return;
            if (!shutdownRequested) beginShutdown();
            if (ownsRest) rest.close();
            events.shutdownNow();
            finish(true);
        }
    }

    /**
     * Shuts down immediately; equivalent to {@link #shutdownNow()}.
     */
    @Override
    public void close() {
        shutdownNow();
    }

    /**
     * Waits without a time limit until the client has shut down or failed permanently.
     * This does not start a shutdown; call {@link #shutdown()} first.
     *
     * <p>This may return before listeners have received the final {@link StatusChangeEvent} and the
     * {@link com.swadedev.micup4j.events.session.ShutdownEvent}; termination does not wait for the event thread, so
     * calling this method from a listener cannot deadlock.
     *
     * @return always {@code true}
     * @throws InterruptedException  if the waiting thread is interrupted
     * @throws IllegalStateException if called on a guarded Micup4J thread ({@code micup4j-gateway}, {@code micup4j-rest-scheduler} or {@code micup4j-delay})
     */
    public boolean awaitShutdown() throws InterruptedException {
        return awaitShutdown(0, TimeUnit.MILLISECONDS);
    }

    /**
     * Waits until the client has shut down or failed permanently, or until the timeout elapses.
     * This does not start a shutdown; call {@link #shutdown()} first.
     *
     * <p>This may return before listeners have received the final {@link StatusChangeEvent} and the
     * {@link com.swadedev.micup4j.events.session.ShutdownEvent}; termination does not wait for the event thread, so
     * calling this method from a listener cannot deadlock.
     *
     * @param timeout the maximum time to wait; {@link Duration#ZERO} waits without a time limit, as in JDA
     * @return {@code true} if the client terminated, {@code false} if the timeout elapsed first
     * @throws InterruptedException  if the waiting thread is interrupted
     * @throws IllegalStateException if called on a guarded Micup4J thread ({@code micup4j-gateway}, {@code micup4j-rest-scheduler} or {@code micup4j-delay})
     */
    public boolean awaitShutdown(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        long nanos;
        try { nanos = timeout.toNanos(); } catch (ArithmeticException tooLong) { nanos = timeout.isNegative() ? -1 : Long.MAX_VALUE; }
        return awaitShutdown(nanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Waits until the client has shut down or failed permanently, or until the timeout elapses.
     * This does not start a shutdown; call {@link #shutdown()} first.
     *
     * <p>This may return before listeners have received the final {@link StatusChangeEvent} and the
     * {@link com.swadedev.micup4j.events.session.ShutdownEvent}; termination does not wait for the event thread, so
     * calling this method from a listener cannot deadlock.
     *
     * @param duration the maximum time to wait; {@code 0} waits without a time limit, as in JDA
     * @param unit     the unit of {@code duration}
     * @return {@code true} if the client terminated, {@code false} if the timeout elapsed first
     * @throws InterruptedException  if the waiting thread is interrupted
     * @throws IllegalStateException if called on a guarded Micup4J thread ({@code micup4j-gateway}, {@code micup4j-rest-scheduler} or {@code micup4j-delay})
     */
    public boolean awaitShutdown(long duration, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(unit, "unit");
        ThreadGuards.checkBlockingAllowed("awaitShutdown");
        try {
            if (duration == 0) terminated.get();
            else terminated.get(duration, unit);
        } catch (ExecutionException | CancellationException failed) {
            return true; // Permanent failure is a terminal state too.
        } catch (TimeoutException timedOut) {
            return false;
        }
        return true;
    }

    /**
     * Runs the shutdown steps that must happen once, under {@link #lifecycle}. Every step is non-blocking.
     *
     * @return the REST drain future
     */
    private CompletableFuture<Void> beginShutdown() {
        shutdownRequested = true;
        updateStatus(Status.SHUTTING_DOWN);
        ready.completeExceptionally(new IllegalStateException("Client closed"));
        discovery.cancel(true);
        if (gateway != null) gateway.close();
        // The event executor keeps accepting work until finish() so the final SHUTDOWN status and ShutdownEvent reach
        // listeners in order on the event thread. Queued Gateway events are still dropped by the shutdownRequested
        // check in enqueueEvent.
        return ownsRest ? rest.shutdown(shutdownTimeout) : CompletableFuture.completedFuture(null);
    }

    /**
     * Releases the remaining resources and completes {@link #termination()}. Runs at most once.
     *
     * @param hard whether in-flight HTTP exchanges are aborted rather than allowed to close normally
     */
    private void finish(boolean hard) {
        synchronized (lifecycle) {
            if (closed) return;
            StatusChangeEvent change = transition(Status.SHUTDOWN);
            closed = true;
            clearEntityState();
            pendingEvents.forEach(event -> event.completeExceptionally(new IllegalStateException("Client closed")));
            deliverFinalEvents(change);
            if (hard) http.shutdownNow(); else http.shutdown();
            terminated.complete(null);
        }
    }

    /**
     * Delivers the final {@code SHUTDOWN} status change and the {@link com.swadedev.micup4j.events.session.ShutdownEvent}
     * exactly once. Runs under {@link #lifecycle} from {@link #finish(boolean)}, which runs at most once, and never blocks.
     *
     * <p>After a graceful shutdown the events are queued on the still-running event executor, so they follow every
     * diagnostic queued earlier and listeners keep running one at a time; the executor is shut down right after. When the
     * executor is already gone ({@link #shutdownNow()} or a prior permanent failure) or rejects the events, they are
     * delivered on a fresh {@code micup4j-shutdown} virtual thread instead, never on a scheduler or the caller's thread.
     * That thread waits for the shut-down executor to terminate, bounded by {@link #FINAL_EVENTS_WAIT}, before
     * delivering, so the final events still follow every earlier listener call.
     *
     * @param change the final status change, or {@code null} if the status was already {@code SHUTDOWN}
     */
    private void deliverFinalEvents(StatusChangeEvent change) {
        List<GenericEvent> finalEvents = new ArrayList<>(2);
        if (change != null) finalEvents.add(change);
        finalEvents.add(new com.swadedev.micup4j.events.session.ShutdownEvent(this, java.time.OffsetDateTime.now(),
                com.swadedev.micup4j.events.session.ShutdownEvent.NORMAL_CLOSURE));
        Runnable deliver = () -> {
            for (GenericEvent event : finalEvents) notifyListeners(event);
        };
        boolean queued = false;
        if (!events.isShutdown()) {
            try {
                events.execute(deliver);
                queued = true;
            } catch (RejectedExecutionException full) {
                // The bounded queue is full; fall back to a dedicated thread below.
            }
        }
        // Idempotent; after shutdownNow() or a permanent failure the executor is already shut down.
        events.shutdown();
        if (!queued) Thread.ofVirtual().name("micup4j-shutdown").start(() -> {
            awaitEventThread();
            deliver.run();
        });
    }

    /**
     * Waits, on the {@code micup4j-shutdown} thread only, until the event executor has terminated or
     * {@link #FINAL_EVENTS_WAIT} elapsed, so the fallback final events do not run concurrently with a listener that is
     * still running and follow every diagnostic queued earlier. The wait is bounded so a listener that never returns
     * cannot suppress the final events. It never delays {@link #termination()}, which {@link #finish(boolean)} completes
     * independently, so a listener blocked in {@link #awaitShutdown()} still returns and lets the executor terminate.
     */
    private void awaitEventThread() {
        try {
            events.awaitTermination(FINAL_EVENTS_WAIT.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); // Deliver anyway; the listeners see the flag on this thread only.
        }
    }

    /** Runs every listener for one local event; a failing listener is counted and reported without details. */
    private void notifyListeners(GenericEvent event) {
        for (EventListener listener : listeners) {
            try { listener.onEvent(event); }
            catch (Throwable error) {
                listenerFailures.increment();
                reportFailure(new IllegalStateException("Listener failed for " + event.getType()));
            }
        }
        deliveredEvents.increment();
    }
}
