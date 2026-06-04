package com.github.kdgaming0.skyrecipes.mixin;

import cc.cassian.rrv.common.config.instances.RecipeCategoryConfig;
import cc.cassian.rrv.common.gui.RecipeCategoryConfigScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;
import java.util.stream.Stream;

/**
 * Hides vanilla Minecraft recipe categories from RRV's category config menu.
 *
 * <p>Redirects the category stream in {@code RecipeCategoryConfigScreen.init()} to
 * filter out any category whose identifier namespace is {@code minecraft}. This keeps
 * the config menu clean and focused on SkyBlock recipe categories only.</p>
 */
@Mixin(RecipeCategoryConfigScreen.class)
public class RecipeCategoryConfigScreenMixin {

    @Redirect(method = "init",
        at = @At(value = "INVOKE", target = "Ljava/util/Collection;stream()Ljava/util/stream/Stream;"))
    private Stream<RecipeCategoryConfig.ConfiguredRecipeCategory> skyrecipes$filterVanillaCategories(
            Collection<RecipeCategoryConfig.ConfiguredRecipeCategory> collection) {
        return collection.stream()
            .filter(cat -> !cat.id().getNamespace().equals("minecraft"));
    }
}
