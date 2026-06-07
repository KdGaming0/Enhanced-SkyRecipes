package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Abstract base for all SkyRecipes custom RRV recipe types.
 *
 * <p>Eliminates the ~40 lines of identical boilerplate ({@code INSTANCE},
 * {@code getDisplayName}, {@code getId}, {@code getIcon}, {@code getPriority},
 * etc.) that were duplicated across every recipe-type class.</p>
 */
public abstract class AbstractSkyblockRecipeType implements ReliableClientRecipeType {

    private final Identifier id;
    private final Component displayName;
    private final int displayWidth;
    private final int displayHeight;
    private final @Nullable Identifier guiTexture;
    private final int slotCount;
    private final ItemStack icon;
    private final int priority;

    protected AbstractSkyblockRecipeType(String id, String displayName,
                                         int displayWidth, int displayHeight,
                                         @Nullable Identifier guiTexture,
                                         int slotCount, ItemStack icon, int priority) {
        this.id = Identifier.fromNamespaceAndPath("skyrecipes", id);
        this.displayName = Component.literal(displayName);
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
        this.guiTexture = guiTexture;
        this.slotCount = slotCount;
        this.icon = icon;
        this.priority = priority;
    }

    @Override
    public final Component getDisplayName() {
        return displayName;
    }

    @Override
    public final int getDisplayWidth() {
        return displayWidth;
    }

    @Override
    public final int getDisplayHeight() {
        return displayHeight;
    }

    @Override
    public final @Nullable Identifier getGuiTexture() {
        return guiTexture;
    }

    @Override
    public final int getSlotCount() {
        return slotCount;
    }

    @Override
    public final Identifier getId() {
        return id;
    }

    @Override
    public final ItemStack getIcon() {
        return icon;
    }

    @Override
    public final int getPriority() {
        return priority;
    }
}
