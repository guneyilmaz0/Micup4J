package net.guneyilmaz0.micup4j;

import net.guneyilmaz0.micup4j.internal.DelayScheduler;
import org.junit.jupiter.api.Test;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class DelaySchedulerTest {
    @Test void timerThreadIdlesOutAfterSixtySeconds() {
        var timer = assertInstanceOf(ScheduledThreadPoolExecutor.class, DelayScheduler.timer());
        assertTrue(timer.allowsCoreThreadTimeOut(), "idle timer thread must be allowed to exit");
        assertEquals(60, timer.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals(1, timer.getCorePoolSize());
        assertTrue(timer.getRemoveOnCancelPolicy(), "cancelled timers must not linger in the queue");
    }
}
