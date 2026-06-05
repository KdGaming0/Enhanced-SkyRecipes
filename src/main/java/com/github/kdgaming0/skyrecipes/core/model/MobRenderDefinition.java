package com.github.kdgaming0.skyrecipes.core.model;

import java.util.Map;

/**
 * Mob render definition for entity preview in drop recipes.
 *
 * @param entityType  The Minecraft entity type string (e.g. "Player", "Zombie")
 * @param skinTexture Optional skin texture path (e.g. "neurepo:mobs/alligator.png")
 * @param modifiers   Additional modifiers (scale, parts, etc.)
 */
public record MobRenderDefinition(
        String entityType,
        String skinTexture,
        Map<String, Object> modifiers
) {
}
