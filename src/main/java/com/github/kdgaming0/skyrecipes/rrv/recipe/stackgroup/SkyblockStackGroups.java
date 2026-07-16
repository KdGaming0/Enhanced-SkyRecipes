package com.github.kdgaming0.skyrecipes.rrv.recipe.stackgroup;

import cc.cassian.rrv.common.config.Configs;
import cc.cassian.rrv.common.config.options.ConfiguredStackGroup;
import cc.cassian.rrv.common.recipe.stackgroup.StackGroupManager;
import cc.cassian.rrv.common.recipe.stackgroup.data.AbstractStackGroup;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.core.family.FamilyInfo;
import com.github.kdgaming0.skyrecipes.core.family.FamilyResolver;
import com.github.kdgaming0.skyrecipes.core.family.FamilyType;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor;
import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import com.github.kdgaming0.skyrecipes.mixin.accessor.StackGroupConfigAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Generates and owns the SkyBlock family stack groups exposed to RRV.
 *
 * <p>Groups are rebuilt from {@link FamilyResolver} on a worker thread during the data
 * pipeline's prep phase and published as an immutable snapshot; the render thread then
 * splices them into {@link StackGroupManager#stackGroups} via {@link #injectInto()} —
 * both after each data cycle and after RRV's own stack-group reload (which clears the
 * list). Lookups are O(1) by SkyBlock ID so the per-keystroke grouping pass never scans
 * the group list for SkyBlock stacks.</p>
 */
public final class SkyblockStackGroups {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkyblockStackGroups.class);

    private static final String NAMESPACE = "skyrecipes";
    /** Trailing tier token on tiered display names: "Wheat Minion XI" / "Sharpness 5". */
    private static final Pattern TRAILING_TIER = Pattern.compile("\\s+([IVXLCDM]+|\\d+)$");
    /** NEU pet display-name prefix: "[Lvl {LVL}] Armadillo". */
    private static final Pattern PET_LEVEL_PREFIX = Pattern.compile("^\\[Lvl[^]]*]\\s*");
    private static final Pattern INVALID_PATH_CHARS = Pattern.compile("[^a-z0-9/._-]");

    private record Snapshot(List<SkyblockFamilyStackGroup> groups,
                            Map<String, SkyblockFamilyStackGroup> byMemberId,
                            Map<Identifier, SkyblockFamilyStackGroup> byGroupId) {
    }

    /**
     * Written by {@link #rebuild} on the worker; promoted to {@link #active} only by
     * {@link #injectInto()} on the render thread. RRV's config lookup
     * ({@code StackGroupConfig.getOrDefault}) NPEs for any group id not currently in
     * {@code StackGroupManager.stackGroups}, and several RRV paths perform it — so a
     * group must never be visible to lookups before it is actually in RRV's list.
     */
    private static volatile Snapshot pending;
    /** Volatile: also read by the prewarm worker via {@link #groupFor}. */
    private static volatile Snapshot active;

    private SkyblockStackGroups() {
    }

    /** True when family groups are injected and the user has the feature enabled. */
    public static boolean isActive() {
        return active != null && SkyRecipesConfig.groupTieredItems;
    }

    /**
     * The family group owning the given SkyBlock ID, or null. O(1). Only groups already
     * injected into RRV are returned; callers additionally gate on {@link #isActive()}.
     */
    public static SkyblockFamilyStackGroup groupFor(String skyblockId) {
        Snapshot snap = active;
        return snap != null ? snap.byMemberId().get(skyblockId) : null;
    }

    /**
     * Tier-sorts a family group's member stacks; false for foreign groups. Always claims
     * family groups: falling through would run RRV's own sort, whose config lookup NPEs
     * (see {@link #pending}) and whose saved order is a list of vanilla item ids — unable
     * to distinguish members that all share one vanilla item.
     */
    public static boolean sortIfFamilyGroup(List<ItemStack> items, Identifier groupId) {
        Snapshot snap = active;
        if (snap == null) return false;
        SkyblockFamilyStackGroup group = snap.byGroupId().get(groupId);
        if (group == null) return false;
        items.sort(Comparator.comparingInt(stack -> {
            String id = SkyblockIdExtractor.extract(stack);
            return id != null ? group.orderOf(id) : Integer.MAX_VALUE;
        }));
        return true;
    }

    /**
     * Promotes the pending snapshot and splices its groups into RRV's group list,
     * replacing any from a previous cycle and re-applying RRV's per-group config and
     * ordering invariant. Render thread only ({@code stackGroups} is unsynchronized);
     * being the sole writer of {@link #active} on the reader thread is what keeps
     * "visible to lookups" and "present in RRV's list" in lockstep.
     */
    public static void injectInto() {
        Snapshot snap = pending;
        if (snap == null) snap = active; // RRV reload with no new cycle: re-inject current
        active = snap;

        List<AbstractStackGroup> groups = StackGroupManager.stackGroups;
        groups.removeIf(g -> g instanceof SkyblockFamilyStackGroup);
        if (snap == null || !SkyRecipesConfig.groupTieredItems
                || !Configs.STACK_GROUPS.areStackGroupsEnabled()) {
            return;
        }
        Map<Identifier, ConfiguredStackGroup> configured =
                ((StackGroupConfigAccessor) Configs.STACK_GROUPS).skyrecipes$getConfiguredGroups();
        for (SkyblockFamilyStackGroup group : snap.groups()) {
            ConfiguredStackGroup config = configured.get(group.getId());
            group.isEnabled = config == null || config.enabled();
            group.priority = config != null ? config.priority() : 0;
            groups.add(group);
        }
        // Same comparator as StackGroupManager.reload keeps the list's ordering contract.
        groups.sort(Comparator.<AbstractStackGroup>comparingInt(g -> -g.priority)
                .thenComparing(g -> g.getId().toString()));
        LOGGER.debug("Injected {} SkyBlock family stack groups", snap.groups().size());
    }

    /**
     * Rebuilds the pending snapshot from the given resolver. Worker thread; the result
     * only becomes visible to lookups after the render thread calls {@link #injectInto()}.
     */
    public static void rebuild(FamilyResolver resolver, ItemRegistry items) {
        Map<Identifier, SkyblockFamilyStackGroup> byGroupId = new LinkedHashMap<>();
        Map<String, SkyblockFamilyStackGroup> byMemberId = new HashMap<>();

        for (FamilyInfo family : resolver.getAllFamilies()) {
            if (!family.type().formsStackGroup()) continue;
            if (family.members().size() < 2) continue;

            Identifier id = groupIdFor(family);
            if (id == null || byGroupId.containsKey(id)) continue;

            Map<String, Integer> order = new HashMap<>(family.members().size() * 2);
            int index = 0;
            for (String member : family.members()) {
                order.put(member, index++);
            }

            SkyblockFamilyStackGroup group =
                    new SkyblockFamilyStackGroup(id, groupNameFor(family, items), Map.copyOf(order));
            byGroupId.put(id, group);
            for (String member : family.members()) {
                byMemberId.putIfAbsent(member, group);
            }
        }

        pending = new Snapshot(List.copyOf(byGroupId.values()),
                Map.copyOf(byMemberId), Map.copyOf(byGroupId));
        LOGGER.info("Built {} SkyBlock family stack groups covering {} items",
                byGroupId.size(), byMemberId.size());
    }

    private static Identifier groupIdFor(FamilyInfo family) {
        String path = "family/" + INVALID_PATH_CHARS.matcher(
                family.familyId().toLowerCase(Locale.ROOT)).replaceAll("_");
        try {
            return Identifier.fromNamespaceAndPath(NAMESPACE, path);
        } catch (Exception e) {
            LOGGER.debug("Skipping family with unrepresentable id: {}", family.familyId());
            return null;
        }
    }

    private static Component groupNameFor(FamilyInfo family, ItemRegistry items) {
        if (family.type() == FamilyType.ACCESSORY_CHAIN) {
            // The family id is the chain's root for explicit families (SPEED_RELIC) or
            // "<base>_ACCESSORY" for implicit ones — reduce both to the plain base.
            String base = family.familyId();
            if (base.endsWith("_ACCESSORY")) {
                base = base.substring(0, base.length() - "_ACCESSORY".length());
            } else {
                base = FamilyResolver.extractBaseName(base);
            }
            return Component.literal(prettify(base) + " Accessories");
        }

        if (family.type() == FamilyType.VARIANT_SET) {
            return Component.literal(variantSetName(family, items));
        }

        String firstMember = family.members().iterator().next();
        String display = items.getByInternalName(firstMember)
                .map(NeuItem::displayName)
                .map(TextUtil::stripColorCodes)
                .orElse("");
        if (display.isEmpty()) {
            return Component.literal(prettify(family.familyId()));
        }

        String withoutLevel = PET_LEVEL_PREFIX.matcher(display).replaceFirst("");
        if (!withoutLevel.equals(display)) {
            // Pet: "[Lvl {LVL}] Armadillo" → "Armadillo Pet"
            return Component.literal(withoutLevel.trim() + " Pet");
        }
        if (family.type() == FamilyType.TIERED) {
            if (firstMember.endsWith("_GEM") && FamilyResolver.extractTier(firstMember) > 0) {
                // Gemstone quality is a leading word: "Rough Ruby Gemstone" → "Ruby Gemstone"
                int space = display.indexOf(' ');
                if (space > 0 && space < display.length() - 1) {
                    return Component.literal(display.substring(space + 1));
                }
            }
            display = TRAILING_TIER.matcher(display).replaceFirst("");
        }
        return Component.literal(display);
    }

    /**
     * Names a variant set by what its members' display names share: the common
     * trailing words, pluralized ("Matcha Dye"/"Lucky Dye" → "Dyes"), else the
     * common leading words ("Blobfish BRONZE"/"Blobfish SILVER" → "Blobfish"),
     * else the prettified family id.
     */
    private static String variantSetName(FamilyInfo family, ItemRegistry items) {
        List<String[]> names = new ArrayList<>(family.members().size());
        for (String member : family.members()) {
            String display = items.getByInternalName(member)
                    .map(NeuItem::displayName)
                    .map(TextUtil::stripColorCodes)
                    .orElse("").trim();
            if (display.isEmpty()) continue;
            names.add(display.split("\\s+"));
        }
        if (names.size() >= 2) {
            String suffix = commonEdge(names, true);
            if (!suffix.isEmpty()) {
                return suffix.endsWith("s") ? suffix : suffix + "s";
            }
            String prefix = commonEdge(names, false);
            if (!prefix.isEmpty()) {
                return prefix;
            }
        }
        return prettify(family.familyId());
    }

    /** Words shared by every name at its end ({@code fromEnd}) or start, joined; "" if none. */
    private static String commonEdge(List<String[]> names, boolean fromEnd) {
        String[] first = names.get(0);
        int common = first.length;
        for (int n = 1; n < names.size(); n++) {
            String[] other = names.get(n);
            int limit = Math.min(common, other.length);
            int matched = 0;
            while (matched < limit) {
                String a = fromEnd ? first[first.length - 1 - matched] : first[matched];
                String b = fromEnd ? other[other.length - 1 - matched] : other[matched];
                if (!a.equalsIgnoreCase(b)) break;
                matched++;
            }
            common = matched;
            if (common == 0) return "";
        }
        // A full match means one member's name is entirely shared words — use it as-is.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < common; i++) {
            if (i > 0) sb.append(' ');
            sb.append(fromEnd ? first[first.length - common + i] : first[i]);
        }
        return sb.toString();
    }

    /** "WHEAT_GENERATOR" → "Wheat Generator". */
    private static String prettify(String familyId) {
        String[] words = familyId.split("[_;]+");
        List<String> parts = new ArrayList<>(words.length);
        for (String word : words) {
            if (!word.isEmpty()) parts.add(TextUtil.capitalize(word));
        }
        return String.join(" ", parts);
    }
}
