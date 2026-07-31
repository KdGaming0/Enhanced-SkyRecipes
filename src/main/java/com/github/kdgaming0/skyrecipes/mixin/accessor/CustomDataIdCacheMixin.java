package com.github.kdgaming0.skyrecipes.mixin.accessor;

import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdHolder;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Gives {@link CustomData} a field to memoize its SkyBlock id in.
 *
 * <p>Replaces the Guava {@code MapMaker().weakKeys()} map {@code SkyblockIdExtractor} used to
 * key by component identity. That map was already identity-based, so the semantics are
 * unchanged — but every lookup paid a hash, a segment selection and a weak-reference
 * dereference, and {@code extract} runs per inventory slot per frame from slot highlighting
 * and from RRV's {@code getGroupForItem}. Spark attributed ~0.15% of render-thread time to
 * {@code MapMakerInternalMap.get} alone. A field read is free by comparison, and the memo's
 * lifetime becomes exactly the component's, so no eviction policy is needed at all.</p>
 *
 * <p>{@code CustomData} is a plain {@code final class} in MC 26.1.2 — a private final
 * {@code tag} plus hand-written {@code equals}/{@code hashCode}, not a record — so adding a
 * field is straightforward.</p>
 *
 * <p><b>Threading:</b> the field is deliberately not {@code volatile}. {@code extract} runs on
 * the render thread, the pipeline workers and parallel rebuild streams, so two threads can
 * race to fill it; the worst outcome is that both compute the same id and one write wins.
 * A torn read is impossible: {@code String} has final fields, so the JMM guarantees a racily
 * published instance is still fully visible. Same reasoning as {@code String.hash}.</p>
 */
@Mixin(CustomData.class)
public class CustomDataIdCacheMixin implements SkyblockIdHolder {

    @Unique
    private String skyrecipes$cachedSkyblockId;

    @Override
    public String skyrecipes$getCachedId() {
        return skyrecipes$cachedSkyblockId;
    }

    @Override
    public void skyrecipes$setCachedId(String id) {
        skyrecipes$cachedSkyblockId = id;
    }
}
