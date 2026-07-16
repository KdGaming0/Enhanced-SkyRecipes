package com.github.kdgaming0.skyrecipes.mixin.accessor;

import cc.cassian.rrv.common.config.instances.StackGroupConfig;
import cc.cassian.rrv.common.config.options.ConfiguredStackGroup;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.LinkedHashMap;

/**
 * Read access to RRV's per-group config map. {@code StackGroupConfig.getOrDefault} is
 * unsafe for groups not currently registered in {@code StackGroupManager.stackGroups}:
 * {@code Map.getOrDefault} evaluates its default argument eagerly, and RRV's default is
 * {@code StackGroupManager.getGroup(id).asConfiguredGroup()} — an NPE when the group is
 * absent. SkyRecipes reads the map directly and supplies its own fallback.
 */
@Mixin(value = StackGroupConfig.class, remap = false)
public interface StackGroupConfigAccessor {

    @Accessor("STACK_GROUPS")
    LinkedHashMap<Identifier, ConfiguredStackGroup> skyrecipes$getConfiguredGroups();
}
