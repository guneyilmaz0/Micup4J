package net.guneyilmaz0.micup4j.interactions.components;

/** A component placed inside an {@link ActionRow}: a button, a select menu or a text input. */
public interface ItemComponent extends Component {
    /** @return how many of the five row slots this component fills */
    default int getMaxPerRow() { return 1; }
}
