package com.swadedev.micup4j.interactions.components;

import java.util.List;

/** A top-level component holding item components. Micup supports {@link ActionRow}. */
public interface LayoutComponent extends Component {
    /**
     * Returns the contained components, immutable.
     *
     * @return the contained components, immutable
     */
    List<ItemComponent> getComponents();
}
