package com.github.kdgaming0.skyrecipes.core.registry;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;

import java.util.*;

/**
 * Thread-safe registry of all parsed NEU items.
 */
public final class ItemRegistry {

    private final Map<String, NeuItem> byInternalName;

    public ItemRegistry(Collection<NeuItem> items) {
        Map<String, NeuItem> map = new HashMap<>(items.size());
        for (NeuItem item : items) {
            map.put(item.internalName(), item);
        }
        this.byInternalName = Collections.unmodifiableMap(map);
    }

    public Optional<NeuItem> getByInternalName(String internalName) {
        return Optional.ofNullable(byInternalName.get(internalName));
    }

    public Collection<NeuItem> getAllItems() {
        return byInternalName.values();
    }

    public int size() {
        return byInternalName.size();
    }

    public boolean contains(String internalName) {
        return byInternalName.containsKey(internalName);
    }
}
