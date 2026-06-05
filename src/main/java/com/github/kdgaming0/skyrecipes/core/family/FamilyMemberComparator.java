package com.github.kdgaming0.skyrecipes.core.family;

import java.util.Comparator;

/**
 * Comparator for SkyBlock internal names that orders tiered family members
 * logically (lower tiers first).
 *
 * <p>Sort order:</p>
 * <ol>
 *   <li>Numeric tier ascending (extracted from trailing {@code _N} or {@code ;N})</li>
 *   <li>Internal name ascending (tie-breaker for non-tiered items)</li>
 * </ol>
 */
public final class FamilyMemberComparator implements Comparator<String> {

    public static final FamilyMemberComparator INSTANCE = new FamilyMemberComparator();

    private FamilyMemberComparator() {
    }

    @Override
    public int compare(String a, String b) {
        int tierA = FamilyResolver.extractTier(a);
        int tierB = FamilyResolver.extractTier(b);
        if (tierA != tierB) {
            return Integer.compare(tierA, tierB);
        }
        return a.compareTo(b);
    }
}
