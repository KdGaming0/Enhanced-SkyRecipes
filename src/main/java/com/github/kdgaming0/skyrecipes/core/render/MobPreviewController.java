package com.github.kdgaming0.skyrecipes.core.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages temporary {@link LivingEntity} instances for drop-recipe previews.
 *
 * <p>Entities are created on demand and must be explicitly disposed to avoid
 * leaking client-side world references.</p>
 */
public final class MobPreviewController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobPreviewController.class);

    private static final Map<String, EntityType<? extends LivingEntity>> MOB_MAP = new HashMap<>();

    static {
        // Common SkyBlock mob mappings (hardcoded fallback until NEU mob data is in binary)
        MOB_MAP.put("ZOMBIE", EntityType.ZOMBIE);
        MOB_MAP.put("SKELETON", EntityType.SKELETON);
        MOB_MAP.put("SPIDER", EntityType.SPIDER);
        MOB_MAP.put("CREEPER", EntityType.CREEPER);
        MOB_MAP.put("ENDERMAN", EntityType.ENDERMAN);
        MOB_MAP.put("BLAZE", EntityType.BLAZE);
        MOB_MAP.put("WITCH", EntityType.WITCH);
        MOB_MAP.put("SLIME", EntityType.SLIME);
        MOB_MAP.put("MAGMA_CUBE", EntityType.MAGMA_CUBE);
        MOB_MAP.put("GHAST", EntityType.GHAST);
        MOB_MAP.put("PIGLIN", EntityType.PIGLIN);
        MOB_MAP.put("PIGLIN_BRUTE", EntityType.PIGLIN_BRUTE);
        MOB_MAP.put("ZOMBIFIED_PIGLIN", EntityType.ZOMBIFIED_PIGLIN);
        MOB_MAP.put("WITHER_SKELETON", EntityType.WITHER_SKELETON);
        MOB_MAP.put("CAVE_SPIDER", EntityType.CAVE_SPIDER);
        MOB_MAP.put("SILVERFISH", EntityType.SILVERFISH);
        MOB_MAP.put("ENDER_DRAGON", EntityType.ENDER_DRAGON);
        MOB_MAP.put("WITHER", EntityType.WITHER);
        MOB_MAP.put("GUARDIAN", EntityType.GUARDIAN);
        MOB_MAP.put("ELDER_GUARDIAN", EntityType.ELDER_GUARDIAN);
        MOB_MAP.put("IRON_GOLEM", EntityType.IRON_GOLEM);
        MOB_MAP.put("SNOW_GOLEM", EntityType.SNOW_GOLEM);
        MOB_MAP.put("WOLF", EntityType.WOLF);
        MOB_MAP.put("ZOGLIN", EntityType.ZOGLIN);
        MOB_MAP.put("HOGLIN", EntityType.HOGLIN);
    }

    private MobPreviewController() {
    }

    /**
     * Create a preview entity for the given mob identifier.
     *
     * @param mobId internal name or mob identifier (e.g. "ZOMBIE", "SKELETON")
     * @return a living entity instance, or {@code null} if creation failed
     */
    @SuppressWarnings("unchecked")
    public static LivingEntity createEntity(String mobId) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }

        EntityType<? extends LivingEntity> type = resolveType(mobId);
        if (type == null) {
            LOGGER.debug("No entity type mapping for mobId: {}", mobId);
            return null;
        }

        try {
            LivingEntity entity = type.create(level, EntitySpawnReason.COMMAND);
            if (entity == null) {
                return null;
            }
            // Position entity at origin for preview
            entity.setPos(0, 0, 0);
            entity.yRotO = 0;
            entity.setYRot(0);
            return entity;
        } catch (Exception e) {
            LOGGER.debug("Failed to create preview entity for {}: {}", mobId, e.getMessage());
            return null;
        }
    }

    /**
     * Create a Villager entity for the blacksmith preview.
     *
     * @return a Villager instance, or {@code null}
     */
    public static LivingEntity createVillager() {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        try {
            LivingEntity entity = EntityType.VILLAGER.create(level, EntitySpawnReason.COMMAND);
            if (entity == null) {
                return null;
            }
            entity.setPos(0, 0, 0);
            entity.yRotO = 0;
            entity.setYRot(0);
            return entity;
        } catch (Exception e) {
            LOGGER.debug("Failed to create villager preview: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Dispose of a previously created preview entity.
     *
     * @param entity the entity to clean up
     */
    public static void disposeEntity(LivingEntity entity) {
        if (entity != null) {
            try {
                entity.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            } catch (Exception e) {
                LOGGER.debug("Failed to dispose preview entity: {}", e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static EntityType<? extends LivingEntity> resolveType(String mobId) {
        if (mobId == null) {
            return null;
        }

        // Direct map lookup
        EntityType<? extends LivingEntity> type = MOB_MAP.get(mobId.toUpperCase());
        if (type != null) {
            return type;
        }

        // Try parsing as a modern identifier
        try {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(mobId.toLowerCase());
            if (id != null) {
                EntityType<?> foundType =
                        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
                if (foundType != null && foundType.getBaseClass() != null && foundType.getBaseClass().isAssignableFrom(LivingEntity.class)) {
                    return (EntityType<? extends LivingEntity>) foundType;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }
}
