package com.github.kdgaming0.skyrecipes.core.family;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FamilyResolverPathTest {

    @Test
    void orderedRecipePathStopsAtSelectedMember() {
        FamilyInfo family = family(FamilyType.UPGRADE_CHAIN,
                "DIAMOND", "ENCHANTED_DIAMOND", "ENCHANTED_DIAMOND_BLOCK");

        assertEquals(ordered("DIAMOND", "ENCHANTED_DIAMOND"),
                FamilyResolver.recipePathMembers(family, "ENCHANTED_DIAMOND"));
        assertEquals(ordered("DIAMOND", "ENCHANTED_DIAMOND", "ENCHANTED_DIAMOND_BLOCK"),
                FamilyResolver.recipePathMembers(family, "ENCHANTED_DIAMOND_BLOCK"));
    }

    @Test
    void usageSuccessorIsTheImmediateNextStep() {
        FamilyInfo family = family(FamilyType.UPGRADE_CHAIN,
                "DIAMOND", "ENCHANTED_DIAMOND", "ENCHANTED_DIAMOND_BLOCK");

        assertEquals("ENCHANTED_DIAMOND", FamilyResolver.nextFamilyMember(family, "DIAMOND"));
        assertEquals("ENCHANTED_DIAMOND_BLOCK",
                FamilyResolver.nextFamilyMember(family, "ENCHANTED_DIAMOND"));
        assertNull(FamilyResolver.nextFamilyMember(family, "ENCHANTED_DIAMOND_BLOCK"));
    }

    @Test
    void unorderedSiblingFamiliesStillExpandTogether() {
        FamilyInfo family = family(FamilyType.ARMOR_SET,
                "SET_HELMET", "SET_CHESTPLATE", "SET_LEGGINGS", "SET_BOOTS");

        assertEquals(family.members(), FamilyResolver.recipePathMembers(family, "SET_CHESTPLATE"));
        assertNull(FamilyResolver.nextFamilyMember(family, "SET_CHESTPLATE"));
    }

    @Test
    void unknownSelectedMemberFallsBackToItself() {
        FamilyInfo family = family(FamilyType.TIERED, "PET;0", "PET;1");
        assertEquals(Set.of("OTHER"), FamilyResolver.recipePathMembers(family, "OTHER"));
    }

    private static FamilyInfo family(FamilyType type, String... members) {
        return new FamilyInfo(members[0], type, ordered(members));
    }

    private static Set<String> ordered(String... members) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Collections.addAll(result, members);
        return Collections.unmodifiableSet(result);
    }
}
