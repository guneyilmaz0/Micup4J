package net.guneyilmaz0.micup4j.hooks;
import net.guneyilmaz0.micup4j.events.GenericEvent;
@FunctionalInterface
public interface EventListener {
    void onEvent(GenericEvent event);
}
