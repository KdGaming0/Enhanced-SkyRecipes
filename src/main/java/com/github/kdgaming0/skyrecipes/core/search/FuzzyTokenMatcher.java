package com.github.kdgaming0.skyrecipes.core.search;

import org.jetbrains.annotations.Nullable;

/**
 * Damerau–Levenshtein distance calculator for typo-tolerant token matching.
 *
 * <p>Inputs must already be lowercase; no case normalization is performed here.
 * Supports insertion, deletion, substitution, and transposition operations.
 * Uses iterative DP with two rows to keep space at {@code O(min(m,n))}.
 * Early-exits when the length difference alone exceeds the maximum allowed
 * distance, and aborts the DP once no cell can come back under it.</p>
 */
public final class FuzzyTokenMatcher {

    private static final int[] EMPTY_ROW = new int[0];

    private FuzzyTokenMatcher() {
    }

    /**
     * Reusable DP row buffers for tight matching loops. Not thread-safe:
     * create one per loop and keep it confined to the calling thread.
     */
    public static final class Scratch {
        private int[] rowA = EMPTY_ROW;
        private int[] rowB = EMPTY_ROW;
        private int[] rowC = EMPTY_ROW;

        private void ensureCapacity(int rowLen) {
            if (rowA.length < rowLen) {
                int cap = Math.max(rowLen, 16);
                rowA = new int[cap];
                rowB = new int[cap];
                rowC = new int[cap];
            }
        }
    }

    /**
     * Compute the Damerau–Levenshtein distance between two lowercase strings.
     *
     * @param a first string
     * @param b second string
     * @return edit distance (0 for identical strings)
     */
    public static int distance(String a, String b) {
        return distance(a, b, Integer.MAX_VALUE - 1, null);
    }

    /**
     * Distance with an abort threshold and optional reusable buffers.
     *
     * @param maxDistance once every reachable cell exceeds this, the DP aborts
     *                    and any value {@code > maxDistance} is returned
     * @param scratch     reusable row buffers, or null to allocate fresh ones
     */
    public static int distance(String a, String b, int maxDistance, @Nullable Scratch scratch) {
        if (a == null) a = "";
        if (b == null) b = "";

        String s = a;
        String t = b;
        int m = s.length();
        int n = t.length();

        if (m == 0) return n;
        if (n == 0) return m;

        // Ensure s is the shorter string to minimise space
        if (m > n) {
            String tmpStr = s;
            s = t;
            t = tmpStr;
            int tmpLen = m;
            m = n;
            n = tmpLen;
        }

        int rowLen = m + 1;

        int[] prevPrev;
        int[] prev;
        int[] curr;
        if (scratch != null) {
            scratch.ensureCapacity(rowLen);
            prevPrev = scratch.rowA;
            prev = scratch.rowB;
            curr = scratch.rowC;
        } else {
            prevPrev = new int[rowLen];
            prev = new int[rowLen];
            curr = new int[rowLen];
        }

        for (int i = 0; i <= m; i++) {
            prev[i] = i;
        }

        // Future rows derive from the previous two rows with non-negative cost,
        // so once both row minima exceed maxDistance the result must too.
        int prevRowMin = 0;
        for (int j = 1; j <= n; j++) {
            curr[0] = j;
            int rowMin = j;
            char tChar = t.charAt(j - 1);
            for (int i = 1; i <= m; i++) {
                char sChar = s.charAt(i - 1);
                int cost = (sChar == tChar) ? 0 : 1;

                int deletion = prev[i] + 1;
                int insertion = curr[i - 1] + 1;
                int substitution = prev[i - 1] + cost;
                int dist = Math.min(Math.min(deletion, insertion), substitution);

                // Transposition
                if (i > 1 && j > 1 && sChar == t.charAt(j - 2) && s.charAt(i - 2) == tChar) {
                    dist = Math.min(dist, prevPrev[i - 2] + cost);
                }

                curr[i] = dist;
                if (dist < rowMin) rowMin = dist;
            }
            if (rowMin > maxDistance && prevRowMin > maxDistance) {
                return maxDistance + 1;
            }
            prevRowMin = rowMin;
            // Rotate rows
            int[] tmp = prevPrev;
            prevPrev = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[m];
    }

    /**
     * Check whether two lowercase strings are within the given edit distance.
     *
     * @param query       query string
     * @param candidate   candidate string
     * @param maxDistance maximum allowed distance
     * @return true if distance {@code <= maxDistance}
     */
    public static boolean matches(String query, String candidate, int maxDistance) {
        return matches(query, candidate, maxDistance, null);
    }

    /**
     * {@link #matches(String, String, int)} with reusable buffers for hot loops.
     */
    public static boolean matches(String query, String candidate, int maxDistance,
                                  @Nullable Scratch scratch) {
        if (maxDistance < 0) return false;
        if (query == null) query = "";
        if (candidate == null) candidate = "";

        // Fast path: exact match
        if (query.equals(candidate)) return true;

        // Early exit: length difference alone exceeds threshold
        if (Math.abs(query.length() - candidate.length()) > maxDistance) return false;

        return distance(query, candidate, maxDistance, scratch) <= maxDistance;
    }
}
