package com.github.kdgaming0.skyrecipes.rrv.recipe.stackgroup;

import cc.cassian.rrv.common.recipe.stackgroup.data.AbstractStackGroup;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;

/**
 * An RRV stack group backed by a SkyRecipes item family (one per minion type, pet,
 * enchantment, drill, accessory chain, …). Unlike RRV's shipped groups, membership is
 * decided by the SkyBlock internal name stored in the stack's {@code ExtraAttributes.id},
 * not by the vanilla item type — which is shared across thousands of SkyBlock items.
 *
 * <p>Instances are immutable except for the {@code isEnabled}/{@code priority} fields
 * RRV itself manages; they are built off-thread by {@link SkyblockStackGroups} and only
 * handed to RRV on the render thread.</p>
 */
public final class SkyblockFamilyStackGroup extends AbstractStackGroup {

    /** SkyBlock internal name → position within the family (tier order). */
    private final Map<String, Integer> memberOrder;
    private final String lowercaseName;

    SkyblockFamilyStackGroup(Identifier id, Component name, Map<String, Integer> memberOrder) {
        super(id, name);
        this.memberOrder = memberOrder;
        this.lowercaseName = name.getString().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean match(ItemStack stack) {
        String skyblockId = SkyblockIdExtractor.extract(stack);
        return skyblockId != null && memberOrder.containsKey(skyblockId);
    }

    /** Tier position of a member, or {@link Integer#MAX_VALUE} for non-members. */
    public int orderOf(String skyblockId) {
        Integer order = memberOrder.get(skyblockId);
        return order != null ? order : Integer.MAX_VALUE;
    }

    /** Precomputed for the per-keystroke group-name matching in the search path. */
    public String lowercaseName() {
        return lowercaseName;
    }
}
