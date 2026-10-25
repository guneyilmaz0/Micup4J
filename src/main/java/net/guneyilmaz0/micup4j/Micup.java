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

/** A single bot session. Owns its HTTP connections, event queue and Gateway lifecycle. */
public final class Micup implements AutoCloseable {
    public enum Status {INITIALIZING, CONNECTING, IDENTIFYING, CONNECTED, RECONNECTING, FAILED, SHUTDOWN}

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final RestClient rest;
    private final CopyOnWriteArrayList<EventListener> listeners;
    private final ThreadPoolExecutor events;
    private final Consumer<Throwable> errorHandler;
    private final CompletableFuture<Micup> ready = new CompletableFuture<>();
    private final CompletableFuture<Void> terminated = new CompletableFuture<>();
    private final Object lifecycle = new Object();
    private volatile Status status = Status.INITIALIZING;
    private volatile User selfUser;
    private volatile String applicationId;
    private volatile GatewayClient gateway;
    private volatile boolean closed;
    private final CompletableFuture<HttpResponse<String>> discovery;

    Micup(String token, URI origin, List<EventListener> listeners, List<String> ignored, int restCapacity, int eventCapacity, int shardId, int shardCount, Consumer<Throwable> handler) {
        this.listeners = new CopyOnWriteArrayList<>(listeners);
        errorHandler = handler;
        events = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(eventCapacity),
                Thread.ofPlatform().daemon().name("micup4j-events").factory(), new ThreadPoolExecutor.AbortPolicy());
        rest = new RestClient(http, token, restCapacity);
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
                    gateway = new GatewayClient(this, http, URI.create(endpoints.path("gateway").asText()), token, ignored, shardId, shardCount);
                    gateway.start();
                } catch (Exception e) {
                    gatewayFailed(new IllegalStateException("Invalid instance discovery response"));
                }
            }
        });
    }

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

    public Micup awaitReady() throws InterruptedException, ExecutionException, TimeoutException {
        return awaitReady(Duration.ofSeconds(60));
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

    public RestAction<User> retrieveUserById(String id) {
        return rest.request("GET", "/users/" + Json.id(id), null).map(User::fromJson);
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

    public void updateStatus(Status next) {
        synchronized (lifecycle) {
            if (!closed) status = next;
        }
    }

    public void onReady(JsonNode data) {
        selfUser = User.fromJson(data.path("user"));
        applicationId = data.path("application").path("id").asText(selfUser.id());
        // Mark CONNECTED before waking awaitReady callers.
        updateStatus(Status.CONNECTED);
        ready.complete(this);
    }

    public CompletableFuture<Void> dispatch(String type, long sequence, JsonNode data) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            GenericEvent event = switch (type) {
                case "READY" -> new ReadyEvent(this, sequence, data);
                case "MESSAGE_CREATE" -> new MessageReceivedEvent(this, sequence, data);
                case "INTERACTION_CREATE" -> new InteractionCreateEvent(this, sequence, data);
                default -> new GenericEvent(this, type, sequence, data);
            };
            events.execute(() -> {
                for (EventListener listener : listeners) {
                    try {
                        listener.onEvent(event);
                    } catch (Throwable error) {
                        reportFailure(new IllegalStateException("Listener failed for " + type));
                    }
                }
                completion.complete(null);
            });
        } catch (Exception e) {
            completion.completeExceptionally(e);
        }
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
            status = Status.FAILED;
            ready.completeExceptionally(error);
            if (gateway != null) gateway.close();
            rest.close();
            events.shutdown();
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
            closed = true;
            status = Status.SHUTDOWN;
            ready.completeExceptionally(new IllegalStateException("Client closed"));
            discovery.cancel(true);
            if (gateway != null) gateway.close();
            rest.close();
            events.shutdownNow();
            http.shutdownNow();
            terminated.complete(null);
        }
    }
}
