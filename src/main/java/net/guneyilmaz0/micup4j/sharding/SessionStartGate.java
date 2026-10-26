package net.guneyilmaz0.micup4j.sharding;

/**
 * Decides when a shard may start its session. This is the extension point for coordinating session starts across
 * processes, for example with a distributed lock, so several JVMs running shards of one bot stay within Micup's
 * per-address Identify budget (300 attempts per 60 seconds) and do not start in bursts.
 *
 * <p>Both methods run on the shard manager's starter virtual thread, one shard at a time, and may block. The default
 * {@link #sequential(java.time.Duration)} gate only spaces starts inside this JVM.
 */
public interface SessionStartGate {
    /**
     * Blocks until shard {@code shardId} may connect.
     *
     * @param shardId the shard about to start
     * @throws InterruptedException if the manager is shutting down
     */
    void awaitTurn(int shardId) throws InterruptedException;

    /**
     * Reports that a started shard became ready or that waiting for it ended. Release distributed permits here.
     *
     * @param shardId the shard
     * @param ready   {@code true} if it reached Ready within the start timeout
     */
    default void started(int shardId, boolean ready) {}

    /**
     * Returns a gate that waits {@code spacing} after the previous start before admitting the next shard.
     *
     * @param spacing the minimum time between two session starts in this JVM
     * @return the gate
     */
    static SessionStartGate sequential(java.time.Duration spacing) {
        if (spacing == null || spacing.isNegative()) throw new IllegalArgumentException("Spacing must not be negative");
        return new SessionStartGate() {
            private long next;
            @Override public synchronized void awaitTurn(int shardId) throws InterruptedException {
                long wait = next - System.nanoTime();
                if (wait > 0) java.util.concurrent.TimeUnit.NANOSECONDS.sleep(wait);
                next = System.nanoTime() + spacing.toNanos();
            }
        };
    }
}
