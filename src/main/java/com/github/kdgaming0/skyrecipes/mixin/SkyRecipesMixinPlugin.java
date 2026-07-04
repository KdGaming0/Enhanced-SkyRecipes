package com.github.kdgaming0.skyrecipes.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Conditionally applies mixins that target optional companion mods.
 *
 * <p>Skyblocker is an optional runtime dependency. Mixins that reference its
 * classes must be skipped when it is not loaded so the game does not crash with
 * a {@link ClassNotFoundException} during Mixin transformation.</p>
 */
public class SkyRecipesMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
        // no-op
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".mixin.skyblocker.")) {
            return FabricLoader.getInstance().isModLoaded("skyblocker")
                    && targetClassExists(targetClassName);
        }
        return true;
    }

    /**
     * Resource-level existence check that never class-loads the target, so a
     * renamed or removed Skyblocker class skips the mixin cleanly instead of
     * failing the mixin apply.
     */
    private static boolean targetClassExists(String targetClassName) {
        String resource = targetClassName.replace('.', '/') + ".class";
        return SkyRecipesMixinPlugin.class.getClassLoader().getResource(resource) != null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // no-op
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // no-op
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // no-op
    }
}
