package com.github.kdgaming0.skyrecipes.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Conditionally applies mixins that target optional companion mods.
 *
 * <p>Skyblocker is an optional runtime dependency. Mixins that reference its
 * classes must be skipped when it is not loaded so the game does not crash with
 * a {@link ClassNotFoundException} during Mixin transformation.</p>
 */
public class SkyRecipesMixinPlugin implements IMixinConfigPlugin {

    /**
     * Optional-mod classes that a mixin references in its own body, keyed by mixin.
     *
     * <p>{@link #targetClassExists(String)} only vets the <em>target</em>, which is enough
     * for mixins that target a companion mod directly. Mixins that target a vanilla class
     * but call into Skyblocker or RRV need their references vetted explicitly, otherwise a
     * rename upstream turns into a {@link NoClassDefFoundError} at apply time.</p>
     */
    private static final Map<String, List<String>> REQUIRED_CLASSES = Map.of(
            "com.github.kdgaming0.skyrecipes.mixin.skyblocker.RecipeSlotRarityBackgroundMixin",
            List.of(
                    "de.hysky.skyblocker.skyblock.item.background.ItemBackgroundManager",
                    "de.hysky.skyblocker.utils.Utils",
                    "cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen"
            )
    );

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
                    && targetClassExists(targetClassName)
                    && requiredClassesExist(mixinClassName);
        }
        return true;
    }

    /**
     * Checks the optional-mod classes a mixin references in its body, for mixins whose
     * target alone does not prove those references still resolve.
     */
    private static boolean requiredClassesExist(String mixinClassName) {
        for (String required : REQUIRED_CLASSES.getOrDefault(mixinClassName, List.of())) {
            if (!targetClassExists(required)) {
                return false;
            }
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
