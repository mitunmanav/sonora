package com.mitun.sonora;

/**
 * The linear keyboard tvOS uses for search: one row of letters, moved along with
 * the remote. Pure logic, so it can be tested without a TV.
 */
public final class SearchKeys {
    public static final String SPACE = "space";
    public static final String DELETE = "delete";
    /** Switches between the letters and the digits. */
    public static final String PAGE = "page";
    public static final String VOICE = "voice";
    public static final int MAX_QUERY = 40;

    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";

    private SearchKeys() {
    }

    /**
     * The keys of one page, in order.
     *
     * @param digits true for the digits page
     * @param voice  true when this TV has an assistant to hand a voice search to
     */
    public static String[] keys(boolean digits, boolean voice) {
        String chars = digits ? DIGITS : LETTERS;
        String[] keys = new String[chars.length() + (voice ? 4 : 3)];
        int at = 0;
        keys[at++] = PAGE;
        for (int i = 0; i < chars.length(); i++) {
            keys[at++] = String.valueOf(chars.charAt(i));
        }
        keys[at++] = SPACE;
        keys[at++] = DELETE;
        if (voice) {
            keys[at] = VOICE;
        }
        return keys;
    }

    /** True for a key that puts a character in the query. */
    public static boolean types(String key) {
        return key.length() == 1;
    }

    /** The query after a key. PAGE and VOICE do not change it. */
    public static String apply(String query, String key) {
        if (DELETE.equals(key)) {
            // Whole code points: halving an emoji would leave a lone surrogate.
            return query.isEmpty() ? query
                    : query.substring(0, query.offsetByCodePoints(query.length(), -1));
        }
        if (SPACE.equals(key)) {
            // A space only means something between two words.
            return query.isEmpty() || query.endsWith(" ") || query.length() >= MAX_QUERY
                    ? query : query + " ";
        }
        if (types(key) && query.length() < MAX_QUERY) {
            return query + key;
        }
        return query;
    }

    /** The key one step along. It stops at both ends, as the tvOS keyboard does. */
    public static int move(int index, int step, int count) {
        // No keys means no landing spot: -1, not a fake index into nothing.
        return count < 1 ? -1 : Math.max(0, Math.min(count - 1, index + step));
    }

    /** The key the focus lands on after the page changed, which is the page key. */
    public static int indexOf(String[] keys, String key) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(key)) {
                return i;
            }
        }
        return 0;
    }
}
