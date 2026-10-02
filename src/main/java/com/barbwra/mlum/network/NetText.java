package com.barbwra.mlum.network;

/**
 * Keeps text inside the length a packet field allows.
 *
 * <p>{@code writeUtf} throws on a string longer than its limit, and a throw while encoding a packet
 * that is sent on login disconnects the player - so one over-long line in a config would lock
 * everyone out until it was fixed. Cutting the text here costs the end of a long description and
 * nothing else.</p>
 */
final class NetText {

    private NetText() {
    }

    static String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        int end = max;
        // never split a surrogate pair
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }
}
