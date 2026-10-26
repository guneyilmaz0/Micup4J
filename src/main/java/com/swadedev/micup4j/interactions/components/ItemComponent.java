package com.swadedev.micup4j.interactions.components;

/** A component placed inside an {@link ActionRow}: a button, a select menu or a text input. */
public interface ItemComponent extends Component {
    /**
     * Returns how many of the five row slots this component allows per row.
     *
     * @return how many of the five row slots this component allows per row
     */
    default int getMaxPerRow() { return 1; }
}
