package com.github.kdgaming0.skyrecipes.core.mob;

import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a NEU drop-recipe {@code render} string into a {@link MobPreview} render plan.
 */
public final class MobPreviewResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobPreviewResolver.class);
    private static final String NEU_PREFIX = "@neurepo:";

    private MobPreviewResolver() {
    }

    @Nullable
    public static MobPreview resolve(@Nullable String renderRef, ConstantsRegistry constantsRegistry) {
        if (renderRef == null || renderRef.isEmpty()) return null;

        if (renderRef.startsWith(NEU_PREFIX)) {
            MobRenderDefinition def = constantsRegistry.getMobRender(renderRef);
            if (def == null) {
                LOGGER.debug("No MobRenderDefinition found for '{}'", renderRef);
                return null;
            }
            return toPreview(def);
        }
        return resolveVanilla(renderRef);
    }

    @Nullable
    private static MobPreview toPreview(MobRenderDefinition def) {
        MobPreview base = baseFor(def);
        if (base == null) return null;

        if (def.rider() == null) return base;

        MobPreview riderPreview = toPreview(def.rider());
        if (riderPreview == null) return base;

        return MobPreview.composite(base, riderPreview);
    }

    @Nullable
    private static MobPreview baseFor(MobRenderDefinition def) {
        if (def.isArmorStandSkull()) {
            return MobPreview.skull(def.helmetItemId());
        }
        if ("Player".equals(def.entityKind()) && def.skinPath() != null) {
            return MobPreview.playerSkin(def.skinPath());
        }
        if ("Horse".equals(def.entityKind()) && def.horseKind() != null) {
            EntityType<?> horseType = resolveHorseKind(def.horseKind());
            if (horseType != null) {
                return MobPreview.vanilla(horseType);
            }
        }
        return resolveVanilla(def.entityKind());
    }

    @Nullable
    private static EntityType<?> resolveHorseKind(String horseKind) {
        return switch (horseKind.toLowerCase()) {
            case "skeleton" -> EntityType.SKELETON_HORSE;
            case "zombie" -> EntityType.ZOMBIE_HORSE;
            default -> null;
        };
    }

    @Nullable
    private static MobPreview resolveVanilla(String name) {
        EntityType<?> type = VanillaEntityNames.resolve(name);
        if (type == null) {
            LOGGER.debug("Could not resolve vanilla entity name '{}'.", name);
        }
        return type != null ? MobPreview.vanilla(type) : null;
    }
}
