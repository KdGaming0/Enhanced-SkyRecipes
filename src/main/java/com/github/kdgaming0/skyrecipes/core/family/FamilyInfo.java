package com.github.kdgaming0.skyrecipes.core.family;

import java.util.Set;

/**
 * Immutable description of an item family.
 *
 * @param familyId a human-readable identifier for the family (e.g. "WHEAT_GENERATOR")
 * @param type     the classification that governs expansion behavior
 * @param members  all internal names that belong to this family
 */
public record FamilyInfo(String familyId, FamilyType type, Set<String> members) {
}
