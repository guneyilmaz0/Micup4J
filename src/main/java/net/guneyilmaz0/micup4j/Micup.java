package net.guneyilmaz0.micup4j;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.entities.*;
import net.guneyilmaz0.micup4j.events.*;
import net.guneyilmaz0.micup4j.hooks.EventListener;
import net.guneyilmaz0.micup4j.interactions.SlashCommand;
import net.guneyilmaz0.micup4j.internal.*;
import net.guneyilmaz0.micup4j.requests.RestAction;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.concurrent.atomic.LongAdder;
import net.guneyilmaz0.micup4j.config.*;
import net.guneyilmaz0.micup4j.diagnostics.ClientMetrics;

/** A single bot session. Owns its HTTP connections, event queue and Gateway lifecycle. */
public final class Micup implements AutoCloseable {
    public enum Status {INITIALIZING, CONNECTING, IDENTIFYING, CONNECTED, RECONNECTING, FAILED, SHUTDOWN}

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final RestClient rest;
    private final EntityCache entityCache;
    private final GuildLifecycle guildLifecycle = new GuildLifecycle();
    private final net.guneyilmaz0.micup4j.managers.Presence presence;
    private final CopyOnWriteArrayList<EventListener> listeners;
    private final ThreadPoolExecutor events;
    private final Consumer<Throwable> errorHandler;
    private final int eventCapacity;
    private final LongAdder deliveredEvents = new LongAdder(), listenerFailures = new LongAdder();
    private final Set<CompletableFuture<Void>> pendingEvents = ConcurrentHashMap.newKeySet();
    private volatile long lastEventQueueDelay;
    private final CompletableFuture<Micup> ready = new CompletableFuture<>();
    private final CompletableFuture<Void> terminated = new CompletableFuture<>();
    private final Object lifecycle = new Object();
    private volatile Status status = Status.INITIALIZING;
    private volatile User selfUser;
    private volatile String applicationId;
    private volatile GatewayClient gateway;
    private volatile URI mediaBase;
    private volatile boolean closed;
    private final CompletableFuture<HttpResponse<String>> discovery;

    Micup(String token, URI origin, List<EventListener> listeners, List<String> ignored, int restCapacity, int eventCapacity, int shardId, int shardCount, Consumer<Throwable> handler, RestConfig restConfig, GatewayConfig gatewayConfig, CachePolicy cachePolicy, net.guneyilmaz0.micup4j.managers.Presence.State initialPresence) {
        this.listeners = new CopyOnWriteArrayList<>(listeners);
        entityCache = new EntityCache(cachePolicy);
        presence = new net.guneyilmaz0.micup4j.managers.Presence(this, initialPresence);
        errorHandler = handler;
        this.eventCapacity = eventCapacity;
        events = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(eventCapacity),
                Thread.ofPlatform().daemon().name("micup4j-events").factory(), new ThreadPoolExecutor.AbortPolicy());
        rest = new RestClient(http, token, restCapacity, restConfig);
        URI url = origin.resolve("/.well-known/fluxer");
        discovery = http.sendAsync(HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(20)).header("Accept", "application/json").header("User-Agent", net.guneyilmaz0.micup4j.MicupVersion.getUserAgent()).GET().build(), HttpResponse.BodyHandlers.ofString());
        discovery.whenComplete((response, failure) -> {
            synchronized (lifecycle) {
                if (closed) return;
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

    public net.guneyilmaz0.micup4j.managers.Presence getPresence() { return presence; }
    /** Signals one replaceable desired presence, not an unbounded stream of Gateway commands. */
    public void presenceChanged() { var connection = gateway; if (connection != null) connection.updatePresence(); }

    public Status getStatus() {
        return status;
    }

    public User getSelfUser() {
        if (selfUser == null) throw new IllegalStateException("Client is not ready");
        return selfUser;
    }

    public String getApplicationId() {
        if (applicationId == null) throw new IllegalStateException("Application ID is unavailable before Ready");
        return applicationId;
    }

    public RestClient getRestClient() {
        return rest;
    }

    /** The instance's discovered Media Proxy base URL, including any deployment path prefix. */
    public URI getMediaBase() {
        if (mediaBase == null) throw new IllegalStateException("Instance discovery did not publish a media endpoint");
        return mediaBase;
    }

    public long getGatewayPing() {
        return gateway == null ? -1 : gateway.getPing();
    }

    public CompletableFuture<Micup> ready() {
        return ready.copy();
    }

    /** Completes on shutdown, or exceptionally when the client fails permanently. */
    public CompletableFuture<Void> termination() {
        return terminated.copy();
    }

    public Micup awaitReady() throws InterruptedException {
        try { return ready.get(); }
        catch (ExecutionException error) { throw new IllegalStateException("Micup failed before Ready", error.getCause()); }
    }

    public Micup awaitReady(Duration timeout) throws InterruptedException, ExecutionException, TimeoutException {
        return ready.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public Micup addEventListener(EventListener listener) {
        listeners.add(Objects.requireNonNull(listener));
        return this;
    }

    public Micup removeEventListener(EventListener listener) {
        listeners.remove(listener);
        return this;
    }

    public MessageChannel getMessageChannelById(String id) {
        return new MessageChannel(this, id);
    }

    /** Cached text channels only; uncached and non-text channel IDs return null. */
    public net.guneyilmaz0.micup4j.entities.channel.concrete.TextChannel getTextChannelById(String id) {
        Json.id(id);
        for (Guild guild : entityCache.getGuilds()) {
            GuildChannel channel = guild.getChannelById(id);
            if (channel != null && channel.rawType() == 0)
                return new net.guneyilmaz0.micup4j.entities.channel.concrete.TextChannel(this, id, guild.id(), channel.name());
        }
        return null;
    }
    public net.guneyilmaz0.micup4j.entities.channel.concrete.TextChannel getTextChannelById(long id) {
        return getTextChannelById(Long.toUnsignedString(id));
    }

    public RestAction<User> retrieveUserById(String id) {
        return rest.request("GET", "/users/" + Json.id(id), null).map(data -> User.fromJson(data).withClient(this));
    }

    /** Cached snapshot only; absence means uncached, not necessarily inaccessible or nonexistent. */
    public Guild getGuildById(String id) { return findGuildById(id).orElse(null); }
    public Guild getGuildById(long id) { return getGuildById(Long.toUnsignedString(id)); }
    public Optional<Guild> findGuildById(String id) { return entityCache.getGuild(Json.id(id)).map(guild -> guild.withClient(this)); }
    /** A lightweight ID reference for REST operations when entity caching is disabled. */
    public Guild getGuildReferenceById(String id) {
        Guild guild = getGuildById(id);
        return guild == null ? new Guild(Json.id(id), "", null, false, List.of(), List.of(), List.of(), false, false).withClient(this) : guild;
    }

    public List<Guild> getGuilds() { return entityCache.getGuilds().stream().map(guild -> guild.withClient(this)).toList(); }

    /** HTTP reads return detached snapshots and never race Gateway updates into the cache. */
    public RestAction<Guild> retrieveGuildById(String id) {
        return rest.request("GET", "/guilds/" + Json.id(id), null).map(data -> Guild.fromJson(data).withClient(this));
    }

    public RestAction<List<Role>> retrieveRoles(String guildId) {
        String id = Json.id(guildId);
        return rest.request("GET", "/guilds/" + id + "/roles", null)
                .map(data -> readList(data, Integer.MAX_VALUE, value -> Role.fromJson(id, value)));
    }

    public RestAction<List<GuildChannel>> retrieveGuildChannels(String guildId) {
        String id = Json.id(guildId);
        return rest.request("GET", "/guilds/" + id + "/channels", null)
                .map(data -> readList(data, Integer.MAX_VALUE, value -> GuildChannel.fromJson(id, value)));
    }

    public RestAction<GuildChannel> retrieveGuildChannelById(String channelId) {
        return rest.request("GET", "/channels/" + Json.id(channelId), null)
                .map(data -> GuildChannel.fromJson(data.path("guild_id").asText(), data));
    }

    public RestAction<Member> retrieveMemberById(String guildId, String userId) {
        String id = Json.id(guildId);
        return rest.request("GET", "/guilds/" + id + "/members/" + Json.id(userId), null)
                .map(data -> Member.fromJson(id, data).withClient(this));
    }

    public RestAction<Member> retrieveSelfMember(String guildId) {
        String id = Json.id(guildId);
        return rest.request("GET", "/guilds/" + id + "/members/@me", null).map(data -> Member.fromJson(id, data).withClient(this));
    }

    /** One bounded page in ascending user-ID order. Pass the final returned ID as the next exclusive cursor. */
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

    public RestAction<Guild> modifyGuildName(String guildId, String name) {
        return rest.request("PATCH", "/guilds/" + Json.id(guildId), Json.object().put("name", boundedName(name, 100)))
                .map(data -> Guild.fromJson(data).withClient(this));
    }

    /** Null clears the nickname. The server enforces hierarchy and permissions. */
    public RestAction<Member> modifyMemberNickname(String guildId, String userId, String nickname) {
        String id = Json.id(guildId);
        if (nickname != null) boundedName(nickname, 32);
        return rest.request("PATCH", "/guilds/" + id + "/members/" + Json.id(userId), Json.object().put("nick", nickname))
                .map(data -> Member.fromJson(id, data).withClient(this));
    }

    public RestAction<Void> addMemberRole(String guildId, String userId, String roleId) {
        return memberRole("PUT", guildId, userId, roleId);
    }

    public RestAction<Void> removeMemberRole(String guildId, String userId, String roleId) {
        return memberRole("DELETE", guildId, userId, roleId);
    }

    private RestAction<Void> memberRole(String method, String guildId, String userId, String roleId) {
        return rest.request(method, "/guilds/" + Json.id(guildId) + "/members/" + Json.id(userId) + "/roles/" + Json.id(roleId), null)
                .map(value -> null);
    }

    /** Omit permissions with null to inherit the everyone mask; feature-gated bits follow server defaults. */
    public RestAction<Role> createRole(String guildId, String name, int color,
                                      net.guneyilmaz0.micup4j.permissions.PermissionSet permissions) {
        String id = Json.id(guildId);
        if (color < 0 || color > 0xFFFFFF) throw new IllegalArgumentException("Color must be a 24-bit RGB value");
        var body = Json.object().put("name", boundedName(name, 100)).put("color", color);
        if (permissions != null) body.put("permissions", permissions.toRequestValue());
        return rest.request("POST", "/guilds/" + id + "/roles", body).map(data -> Role.fromJson(id, data));
    }

    public RestAction<Role> modifyRoleName(String guildId, String roleId, String name) {
        String id = Json.id(guildId);
        return rest.request("PATCH", "/guilds/" + id + "/roles/" + Json.id(roleId), Json.object().put("name", boundedName(name, 100)))
                .map(data -> Role.fromJson(id, data));
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
     * Replaces all commands in the given guild. Call only when command definitions change.
     */
    public RestAction<JsonNode> updateGuildCommands(String guildId, SlashCommand... commands) {
        var body = Json.MAPPER.createArrayNode();
        for (var command : commands) body.add(command.toJson());
        return rest.request("PUT", "/applications/" + Json.id(getApplicationId()) + "/guilds/" + Json.id(guildId) + "/commands", body);
    }

    /**
     * Replaces all global commands.
     */
    public RestAction<JsonNode> updateCommands(SlashCommand... commands) {
        var body = Json.MAPPER.createArrayNode();
        for (var command : commands) body.add(command.toJson());
        return rest.request("PUT", "/applications/" + Json.id(getApplicationId()) + "/commands", body);
    }

    public net.guneyilmaz0.micup4j.requests.restaction.CommandListUpdateAction updateCommands() {
        return new net.guneyilmaz0.micup4j.requests.restaction.CommandListUpdateAction(this, null);
    }
    public net.guneyilmaz0.micup4j.requests.restaction.CommandListUpdateAction updateGuildCommands(String guildId) {
        return new net.guneyilmaz0.micup4j.requests.restaction.CommandListUpdateAction(this, Json.id(guildId));
    }
    public String commandPath(String guildId) {
        return "/applications/" + Json.id(getApplicationId()) + (guildId == null ? "" : "/guilds/" + Json.id(guildId)) + "/commands";
    }
    public RestAction<net.guneyilmaz0.micup4j.interactions.commands.Command> upsertCommand(
            net.guneyilmaz0.micup4j.interactions.commands.build.CommandData command) { return upsertGuildCommand(null, command); }
    public RestAction<net.guneyilmaz0.micup4j.interactions.commands.Command> upsertGuildCommand(String guildId,
            net.guneyilmaz0.micup4j.interactions.commands.build.CommandData command) {
        return rest.request("POST", commandPath(guildId), Objects.requireNonNull(command).toJson())
                .map(net.guneyilmaz0.micup4j.interactions.commands.Command::fromJson);
    }
    public RestAction<List<net.guneyilmaz0.micup4j.interactions.commands.Command>> retrieveCommands() { return retrieveGuildCommands(null); }
    public RestAction<List<net.guneyilmaz0.micup4j.interactions.commands.Command>> retrieveGuildCommands(String guildId) {
        return rest.request("GET", commandPath(guildId), null)
                .map(data -> readList(data, Integer.MAX_VALUE, net.guneyilmaz0.micup4j.interactions.commands.Command::fromJson));
    }
    public RestAction<Void> deleteCommandById(String commandId) { return deleteGuildCommandById(null, commandId); }
    public RestAction<Void> deleteCommandById(long commandId) { return deleteCommandById(Long.toUnsignedString(commandId)); }
    public RestAction<Void> deleteGuildCommandById(String guildId, String commandId) {
        return rest.request("DELETE", commandPath(guildId) + "/" + Json.id(commandId), null).map(ignored -> null);
    }
    public Micup addEventListeners(net.guneyilmaz0.micup4j.hooks.EventListener... values) {
        for (var listener : values) addEventListener(listener); return this;
    }
    public Micup removeEventListeners(net.guneyilmaz0.micup4j.hooks.EventListener... values) {
        for (var listener : values) removeEventListener(listener); return this;
    }
    public RestAction<User> retrieveUserById(long id) { return retrieveUserById(Long.toUnsignedString(id)); }
    public MessageChannel getMessageChannelById(long id) { return getMessageChannelById(Long.toUnsignedString(id)); }

    public ClientMetrics getMetrics() {
        return new ClientMetrics(status, rest.getMetrics(), events.getQueue().size(), eventCapacity,
                deliveredEvents.sum(), listenerFailures.sum(), lastEventQueueDelay, getGatewayPing(),
                gateway == null ? 0 : gateway.getReconnectCount());
    }

    public void updateStatus(Status next) {
        synchronized (lifecycle) {
            if (closed || status == next || (status == Status.FAILED && next != Status.SHUTDOWN)) return;
            Status previous = status;
            status = next;
            dispatchDiagnostic(new StatusChangeEvent(this, previous, next));
        }
    }

    public void onReady(JsonNode data) {
        synchronized (lifecycle) {
            if (closed || status == Status.FAILED) return;
            selfUser = User.fromJson(data.path("user")).withClient(this);
            applicationId = data.path("application").path("id").asText(selfUser.id());
            updateStatus(Status.CONNECTED);
            ready.complete(this);
        }
    }

    public void dispatchDiagnostic(GenericEvent event) {
        // Reserve a queue slot for protocol dispatches; local diagnostics are best-effort.
        if (events.getQueue().remainingCapacity() < 2) return;
        enqueueEvent(event);
    }

    public CompletableFuture<Void> dispatch(String type, long sequence, JsonNode data) {
        try {
            GenericEvent event = switch (type) {
                case "READY" -> new net.guneyilmaz0.micup4j.events.session.ReadyEvent(this, sequence, data);
                case "MESSAGE_CREATE" -> new net.guneyilmaz0.micup4j.events.message.MessageReceivedEvent(this, sequence, data);
                case "INTERACTION_CREATE" -> data.path("type").asInt() == 2
                        ? new net.guneyilmaz0.micup4j.events.interaction.command.SlashCommandInteractionEvent(this, sequence, data)
                        : new InteractionCreateEvent(this, sequence, data);
                default -> new GenericEvent(this, type, sequence, data);
            };
            return enqueueEvent(event);
        } catch (Exception error) { return CompletableFuture.failedFuture(error); }
    }

    private CompletableFuture<Void> enqueueEvent(GenericEvent event) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        long queuedAt = System.nanoTime();
        pendingEvents.add(completion);
        completion.whenComplete((value, error) -> pendingEvents.remove(completion));
        try {
            events.execute(() -> {
                lastEventQueueDelay = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - queuedAt);
                GenericEvent delivered = event;
                try {
                    synchronized (lifecycle) {
                        if (closed) { completion.completeExceptionally(new IllegalStateException("Client closed")); return; }
                        if (status != Status.FAILED) {
                            boolean guildEvent = GuildLifecycle.accepts(event.getType());
                            if (guildEvent || entityCache.accepts(event.getType())) {
                                JsonNode data = event.getData();
                                String guildId = data.path("guild_id").asText(data.path("id").asText(null));
                                Guild before = guildEvent && guildId != null ? getGuildById(guildId) : null;
                                entityCache.apply(event.getType(), data);
                                if (guildEvent) {
                                    Guild after = guildId == null ? null : getGuildById(guildId);
                                    delivered = guildLifecycle.apply(this, event, data, before, after);
                                }
                            }
                        }
                    }
                } catch (RuntimeException invalid) {
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

    public void reportFailure(Throwable error) {
        try {
            errorHandler.accept(error);
        } catch (Throwable ignored) {
            System.getLogger("Micup4J").log(System.Logger.Level.ERROR, "Error handler failed");
        }
    }

    public void gatewayFailed(Throwable error) {
        synchronized (lifecycle) {
            if (closed || status == Status.FAILED) return;
            updateStatus(Status.FAILED);
            ready.completeExceptionally(error);
            if (gateway != null) gateway.close();
            rest.close();
            events.shutdown();
            entityCache.clear();
            guildLifecycle.clear();
            http.shutdown();
            terminated.completeExceptionally(error);
        }
        reportFailure(error);
    }

    public void shutdown() {
        close();
    }

    @Override
    public void close() {
        synchronized (lifecycle) {
            if (closed) return;
            updateStatus(Status.SHUTDOWN);
            closed = true;
            ready.completeExceptionally(new IllegalStateException("Client closed"));
            discovery.cancel(true);
            if (gateway != null) gateway.close();
            rest.close();
            events.shutdownNow();
            entityCache.clear();
            guildLifecycle.clear();
            pendingEvents.forEach(event -> event.completeExceptionally(new IllegalStateException("Client closed")));
            http.shutdownNow();
            terminated.complete(null);
        }
    }
}
