package com.github.kdgaming0.skyrecipes.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

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

    private static final Logger LOGGER = LoggerFactory.getLogger("skyrecipes/mixin-plugin");

    /**
     * RRV methods a mixin's handler declares the argument list of, keyed by mixin. Mixin validates
     * handler descriptors exactly, so a mismatch here would throw during APPLY and crash inside
     * RRV's entrypoint.
     */
    private static final Map<String, List<String>> REQUIRED_TARGET_DESCRIPTORS = Map.of(
            "com.github.kdgaming0.skyrecipes.mixin.overlay.ItemViewOverlayMixin",
            List.of(
                    "<init>()V",
                    "renderItemHighlighting(Lnet/minecraft/client/gui/screens/Screen;"
                            + "Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
                    "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
                    "updateQuery(Ljava/lang/String;)V",
                    "onScreenChanged(Lcc/cassian/rrv/common/overlay/AbstractRrvOverlay$InventoryPositionInfo;)V"
            ),
            "com.github.kdgaming0.skyrecipes.mixin.rrv.ItemViewOverlayWidthMixin",
            List.of(
                    "initForScreen(Lnet/minecraft/client/gui/screens/Screen;"
                            + "Lcc/cassian/rrv/common/overlay/AbstractRrvOverlay$InventoryPositionInfo;)V"
            ),
            "com.github.kdgaming0.skyrecipes.mixin.rrv.SidePanelOverlayWidthMixin",
            List.of(
                    "initForScreen(Lnet/minecraft/client/gui/screens/Screen;"
                            + "Lcc/cassian/rrv/common/overlay/AbstractRrvOverlay$InventoryPositionInfo;)V"
            )
    );

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
        return targetSignaturesMatch(targetClassName, mixinClassName);
    }

    /**
     * Confirms the target still declares each required descriptor, reading its bytecode without
     * class-loading it. An unreadable target is applied unchecked rather than silently dropped.
     */
    private static boolean targetSignaturesMatch(String targetClassName, String mixinClassName) {
        List<String> required = REQUIRED_TARGET_DESCRIPTORS.get(mixinClassName);
        if (required == null) {
            return true;
        }

        ClassNode target;
        try {
            target = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
        } catch (Throwable t) {
            LOGGER.debug("Could not read {} to verify {}; applying it unchecked.",
                    targetClassName, mixinClassName, t);
            return true;
        }

        for (String signature : required) {
            int paren = signature.indexOf('(');
            String name = signature.substring(0, paren);
            String descriptor = signature.substring(paren);

            boolean found = false;
            for (MethodNode method : target.methods) {
                if (method.name.equals(name) && method.desc.equals(descriptor)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                LOGGER.warn("Skipping {}: {} no longer declares {}{}. That feature is disabled; "
                                + "update SkyRecipes if RRV was recently updated.",
                        mixinClassName, targetClassName, name, descriptor);
                return false;
            }
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
