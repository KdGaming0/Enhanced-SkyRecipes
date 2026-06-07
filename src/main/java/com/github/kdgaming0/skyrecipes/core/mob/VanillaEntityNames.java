package com.github.kdgaming0.skyrecipes.core.mob;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves NEU's vanilla-name render strings ({@code "Spider"}, {@code "Mooshroom"},
 * {@code "Eisengolem"}, …) into real {@link EntityType}s on 1.21+.
 *
 * <p>NEU uses a historic mix of naming conventions. This class normalises them.</p>
 */
public final class VanillaEntityNames {

    private static final Map<String, EntityType<?>> ALIASES = new HashMap<>();

    static {
        alias("Mooshroom", EntityType.MOOSHROOM);
        alias("CaveSpider", EntityType.CAVE_SPIDER);
        alias("MagmaCube", EntityType.MAGMA_CUBE);
        alias("GlowSquid", EntityType.GLOW_SQUID);
        alias("Snowman", EntityType.SNOW_GOLEM);
        alias("Eisengolem", EntityType.IRON_GOLEM);
        alias("Pigman", EntityType.ZOMBIFIED_PIGLIN);
        alias("SkeletonHorse", EntityType.SKELETON_HORSE);
        alias("Dragon", EntityType.ENDER_DRAGON);
        alias("Salmom", EntityType.SALMON);
        alias("SiNelverfish", EntityType.SILVERFISH);
    }

    private static void alias(String neuName, EntityType<?> type) {
        ALIASES.put(neuName, type);
    }

    private VanillaEntityNames() {}

    @Nullable
    public static EntityType<?> resolve(String neuName) {
        if (neuName == null || neuName.isEmpty()) return null;

        EntityType<?> aliased = ALIASES.get(neuName);
        if (aliased != null) return aliased;

        Identifier id = Identifier.tryBuild("minecraft", toSnakeCase(neuName));
        if (id == null) return null;

        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    private static String toSnakeCase(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0 && !Character.isUpperCase(name.charAt(i - 1))) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
