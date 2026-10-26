package com.swadedev.micup4j.sharding;

import com.swadedev.micup4j.Micup;
import com.swadedev.micup4j.MicupBuilder;
import com.swadedev.micup4j.OnlineStatus;
import com.swadedev.micup4j.entities.Activity;
import com.swadedev.micup4j.entities.Guild;
import com.swadedev.micup4j.entities.User;
import com.swadedev.micup4j.internal.Json;
import com.swadedev.micup4j.internal.RestClient;
import com.swadedev.micup4j.requests.RestAction;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * The {@link ShardManager} built by {@link DefaultShardManagerBuilder}. A single starter virtual thread starts shards
 * one at a time through the {@link SessionStartGate}, waiting for each Ready (bounded by the start timeout) before the
 * next, which keeps session starts within Micup's per-address Identify budget. Restarts use the same queue.
 */
final class DefaultShardManager implements ShardManager {
    private final int total;
    private final List<Integer> ids;
    private final IntFunction<MicupBuilder> factory;
    private final RestClient rest;
    private final Duration restDrain, startTimeout;
    private final SessionStartGate gate;
    private final Consumer<Throwable> errorHandler;
    private final ConcurrentSkipListMap<Integer, Micup> shards = new ConcurrentSkipListMap<>();
    private final LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
    private final CompletableFuture<Void> terminated = new CompletableFuture<>();
    private final Object lock = new Object();
    private volatile OnlineStatus status;
    private volatile Activity activity;
    private volatile boolean activitySet, shuttingDown;
    private Thread starter;

    DefaultShardManager(int total, List<Integer> ids, IntFunction<MicupBuilder> factory, RestClient rest, Duration restDrain,
                        Duration startTimeout, SessionStartGate gate, Consumer<Throwable> errorHandler) {
        this.total = total; this.ids = ids; this.factory = factory; this.rest = rest; this.restDrain = restDrain;
        this.startTimeout = startTimeout; this.gate = gate; this.errorHandler = errorHandler;
    }

    void start() {
        queue.addAll(ids);
        // Shards are built later on a virtual thread, so hold the JVM from build() until the manager terminates.
        com.swadedev.micup4j.internal.KeepAlive.holdUntil(terminated);
        starter = Thread.ofVirtual().name("micup4j-shard-starter").start(this::runStarter);
    }

    private void runStarter() {
        try {
            while (!shuttingDown) {
                int id = queue.take();
                if (shuttingDown) break;
                gate.awaitTurn(id);
                if (shuttingDown) break;
                Micup shard;
                synchronized (lock) {
                    if (shuttingDown) break;
                    MicupBuilder builder = factory.apply(id);
                    if (status != null) builder.setStatus(status);
                    if (activitySet) builder.setActivity(activity);
                    shard = builder.build();
                    shards.put(id, shard);
                }
                boolean ready = false;
                try { shard.awaitReady(startTimeout); ready = true; }
                catch (TimeoutException timeout) { report(new TimeoutException("Shard " + id + " did not become ready within " + startTimeout.toSeconds() + " s; it keeps connecting")); }
                catch (java.util.concurrent.ExecutionException failure) { report(new IllegalStateException("Shard " + id + " failed to start", failure.getCause())); }
                finally { gate.started(id, ready); }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException error) {
            report(error);
        }
    }

    private void report(Throwable error) {
        try { errorHandler.accept(error); } catch (RuntimeException ignored) { /* A failing handler must not stop the starter. */ }
    }

    @Override public int getShardsTotal() { return total; }
    @Override public List<Micup> getShards() { return List.copyOf(shards.values()); }
    @Override public Micup getShardById(int id) { return shards.get(id); }
    @Override public int getShardsRunning() { return (int) shards.values().stream().filter(s -> s.getStatus() == Micup.Status.CONNECTED).count(); }
    @Override public int getShardsQueued() { return (int) ids.stream().filter(id -> !shards.containsKey(id)).count(); }
    @Override public Map<Integer, Micup.Status> getStatuses() {
        var result = new LinkedHashMap<Integer, Micup.Status>();
        shards.forEach((id, shard) -> result.put(id, shard.getStatus()));
        return Map.copyOf(result);
    }
    @Override public RestClient getRestClient() { return rest; }

    @Override public int getShardIdForGuild(String guildId) {
        return new BigInteger(Json.id(guildId)).shiftRight(22).mod(BigInteger.valueOf(total)).intValue();
    }

    @Override public Guild getGuildById(String id) {
        Micup shard = shards.get(getShardIdForGuild(id));
        return shard == null ? null : shard.getGuildById(id);
    }

    @Override public List<Guild> getGuilds() {
        var guilds = new ArrayList<Guild>();
        shards.values().forEach(shard -> guilds.addAll(shard.getGuilds()));
        return List.copyOf(guilds);
    }

    @Override public double getAverageGatewayPing() {
        return shards.values().stream().mapToLong(Micup::getGatewayPing).filter(ping -> ping >= 0).average().orElse(-1);
    }

    @Override public RestAction<User> retrieveUserById(String id) {
        var shard = shards.isEmpty() ? null : shards.firstEntry().getValue();
        if (shard == null) throw new IllegalStateException("No shard has started");
        return shard.retrieveUserById(id);
    }

    @Override public void setStatus(OnlineStatus value) {
        synchronized (lock) { status = value; shards.values().forEach(shard -> shard.getPresence().setStatus(value)); }
    }

    @Override public void setActivity(Activity value) {
        synchronized (lock) { activity = value; activitySet = true; shards.values().forEach(shard -> shard.getPresence().setActivity(value)); }
    }

    @Override public boolean awaitReady(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        for (int id : ids) {
            Micup shard;
            while ((shard = shards.get(id)) == null) {
                if (System.nanoTime() >= deadline || shuttingDown) return false;
                TimeUnit.MILLISECONDS.sleep(10);
            }
            try { shard.awaitReady(Duration.ofNanos(Math.max(1, deadline - System.nanoTime()))); }
            catch (TimeoutException | java.util.concurrent.ExecutionException notReady) { return false; }
        }
        return true;
    }

    @Override public void restart(int id) {
        if (!ids.contains(id)) throw new IllegalArgumentException("Shard " + id + " is not managed here");
        synchronized (lock) {
            if (shuttingDown) throw new IllegalStateException("Shard manager is shut down");
            Micup old = shards.remove(id);
            if (old != null) old.shutdownNow();
            queue.add(id);
        }
    }

    @Override public void shutdown(int id) {
        Micup shard;
        synchronized (lock) { queue.remove(id); shard = shards.remove(id); }
        if (shard != null) shard.shutdown();
    }

    @Override public void shutdown() {
        List<Micup> stopping;
        synchronized (lock) {
            if (shuttingDown) return;
            shuttingDown = true;
            queue.clear();
            stopping = List.copyOf(shards.values());
        }
        starter.interrupt();
        stopping.forEach(Micup::shutdown);
        // Drain the shared REST client only after every shard stopped submitting work.
        CompletableFuture.allOf(stopping.stream().map(shard -> shard.termination().exceptionally(error -> null)).toArray(CompletableFuture[]::new))
                .thenCompose(ignored -> rest.shutdown(restDrain))
                .whenComplete((ignored, error) -> { rest.close(); terminated.complete(null); });
    }

    @Override public void shutdownNow() {
        List<Micup> stopping;
        synchronized (lock) {
            shuttingDown = true;
            queue.clear();
            stopping = List.copyOf(shards.values());
        }
        if (starter != null) starter.interrupt();
        stopping.forEach(Micup::shutdownNow);
        rest.close();
        terminated.complete(null);
    }

    @Override public boolean awaitShutdown(Duration timeout) throws InterruptedException {
        com.swadedev.micup4j.internal.ThreadGuards.checkBlockingAllowed("awaitShutdown");
        try { terminated.get(timeout.toNanos(), TimeUnit.NANOSECONDS); return true; }
        catch (TimeoutException late) { return false; }
        catch (java.util.concurrent.ExecutionException impossible) { return true; }
    }
}
