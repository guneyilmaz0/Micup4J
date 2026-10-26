package net.guneyilmaz0.micup4j.interactions.components;

import java.util.List;

/** A top-level component holding item components. Micup supports {@link ActionRow}. */
public interface LayoutComponent extends Component {
    /** @return the contained components, immutable */
    List<ItemComponent> getComponents();
}
