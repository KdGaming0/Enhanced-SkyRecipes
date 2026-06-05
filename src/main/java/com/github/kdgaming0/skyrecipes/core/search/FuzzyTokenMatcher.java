package com.github.kdgaming0.skyrecipes.core.search;

/**
 * Damerau–Levenshtein distance calculator for typo-tolerant token matching.
 *
 * <p>Supports insertion, deletion, substitution, and transposition operations.
 * Uses iterative DP with two rows to keep space at {@code O(min(m,n))}.
 * Early-exits when the length difference alone exceeds the maximum allowed
 * distance.</p>
 */
public final class FuzzyTokenMatcher {

    private FuzzyTokenMatcher() {
    }

    /**
     * Compute the Damerau–Levenshtein distance between two strings.
     *
     * @param a first string
     * @param b second string
     * @return edit distance (0 for identical strings)
     */
    public static int distance(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";

        // Normalise to lower case for case-insensitive matching
        String s = a.toLowerCase();
        String t = b.toLowerCase();

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

        int maxDist = m + 1;

        int[] prevPrev = new int[maxDist];
        int[] prev = new int[maxDist];
        int[] curr = new int[maxDist];

        for (int i = 0; i <= m; i++) {
            prev[i] = i;
        }

        char prevSChar = 0;
        for (int j = 1; j <= n; j++) {
            curr[0] = j;
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
                prevSChar = sChar;
            }
            // Rotate rows
            int[] tmp = prevPrev;
            prevPrev = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[m];
    }

    /**
     * Check whether two strings are within the given edit distance.
     *
     * @param query       query string
     * @param candidate   candidate string
     * @param maxDistance maximum allowed distance
     * @return true if distance {@code <= maxDistance}
     */
    public static boolean matches(String query, String candidate, int maxDistance) {
        if (maxDistance < 0) return false;
        if (query == null) query = "";
        if (candidate == null) candidate = "";

        // Fast path: exact match
        if (query.equalsIgnoreCase(candidate)) return true;

        // Early exit: length difference alone exceeds threshold
        if (Math.abs(query.length() - candidate.length()) > maxDistance) return false;

        return distance(query, candidate) <= maxDistance;
    }
}
