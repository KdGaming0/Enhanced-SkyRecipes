package com.github.kdgaming0.skyrecipes.core.util;

/**
 * Per-instance SkyBlock-id memo carried by {@code CustomData} itself.
 *
 * <p>Implemented onto {@code net.minecraft.world.item.component.CustomData} by
 * {@code CustomDataIdCacheMixin}; see {@link SkyblockIdExtractor#extract} for why the id can be
 * cached on the component and what the three states mean.</p>
 *
 * <p>Lives outside the {@code mixin} package on purpose: classes there are not on the normal
 * classpath at runtime, so a duck interface declared beside its mixin fails to load.</p>
 */
public interface SkyblockIdHolder {

    /**
     * @return the memoized internal name, the empty string when this component has been
     * checked and carries no SkyBlock id, or {@code null} when it has not been resolved yet
     */
    String skyrecipes$getCachedId();

    void skyrecipes$setCachedId(String id);
}
