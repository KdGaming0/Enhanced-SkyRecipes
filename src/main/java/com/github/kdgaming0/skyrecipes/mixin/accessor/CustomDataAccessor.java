package com.github.kdgaming0.skyrecipes.mixin.accessor;

import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to {@link CustomData}'s backing tag without the deep copy.
 *
 * <p>MC 26.1.2 exposes only {@code copyTag()}, which deep-copies the whole NBT
 * tree. SkyRecipes reads {@code ExtraAttributes.id} per inventory slot per frame
 * (slot highlighting, recipe lookups), so the copies are pure allocation churn.
 * Callers must treat the returned tag as immutable — mutating it would corrupt
 * the component for everyone sharing the stack.</p>
 */
@Mixin(CustomData.class)
public interface CustomDataAccessor {

    @Accessor("tag")
    net.minecraft.nbt.CompoundTag skyrecipes$getTag();
}
