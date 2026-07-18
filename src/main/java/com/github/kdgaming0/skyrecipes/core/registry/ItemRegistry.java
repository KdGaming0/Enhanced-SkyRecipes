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

    /**
     * Returns the canonical internal name for the given name.
     *
     * <p>If the name ends with {@code _ITEM} and a base item exists with the same
     * {@code itemid} and {@code damage}, the base name is returned. This eliminates
     * duplicate collection-menu artifacts (e.g. {@code CARROT_ITEM} → {@code CARROT}).</p>
     */
    public String getCanonicalName(String internalName) {
        if (internalName == null || internalName.length() <= 5 || !internalName.endsWith("_ITEM")) {
            return internalName;
        }
        String base = internalName.substring(0, internalName.length() - 5);
        NeuItem baseItem = byInternalName.get(base);
        NeuItem item = byInternalName.get(internalName);
        if (baseItem != null && item != null
                && java.util.Objects.equals(baseItem.itemId(), item.itemId())
                && baseItem.damage() == item.damage()) {
            return base;
        }
        return internalName;
    }

    public Optional<NeuItem> getByInternalName(String internalName) {
        return Optional.ofNullable(byInternalName.get(internalName));
    }

    /**
     * Allocation-free variant of {@link #getByInternalName} for hot loops
     * (index building, family resolution, per-frame lookups).
     */
    @org.jetbrains.annotations.Nullable
    public NeuItem getOrNull(String internalName) {
        return internalName != null ? byInternalName.get(internalName) : null;
    }

    /**
     * Look up an item by its canonical name, remapping {@code X_ITEM} to {@code X}
     * when they are true duplicates (same itemid + damage).
     */
    public Optional<NeuItem> getByInternalNameCanonical(String internalName) {
        String canonical = getCanonicalName(internalName);
        return Optional.ofNullable(byInternalName.get(canonical));
    }

    /**
     * Returns all canonical items, filtering out {@code _ITEM} duplicates that have
     * a matching base item.
     */
    public Collection<NeuItem> getCanonicalItems() {
        List<NeuItem> result = new ArrayList<>(byInternalName.size());
        for (NeuItem item : byInternalName.values()) {
            if (item.internalName().equals(getCanonicalName(item.internalName()))) {
                result.add(item);
            }
        }
        return result;
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
