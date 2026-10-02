package com.barbwra.mlum.warehouse.net;

/**
 * The complete set of things a client is allowed to ask for.
 *
 * <p>A closed enum is the point. The client sends one of these plus at most a string and two ints;
 * it never sends an item, a slot index, a price, a lore line or an inventory title. Whatever the
 * action means is re-derived on the server from its own state, so the worst a malicious client can
 * do is request something it is not entitled to and be refused.</p>
 *
 * <p>Ordinals are the wire format, so append only - reordering repoints every value.</p>
 */
public enum TerminalAction {

    /** Ask for a fresh snapshot. Costs a rate-limit token like anything else. */
    REFRESH,
    /** Start an assembly line. Payload: recipe id. */
    START_CRAFT,
    /** Add or remove a crate from the convoy. Payload: crate uuid. */
    TOGGLE_CRATE,
    /** Empty the convoy. */
    CLEAR_SELECTION,
    /** Delete every spoiled crate. */
    PURGE_SPOILED,
    /** Choose the delivery route. Payload: route id. */
    SET_ROUTE,
    /** Authorise the convoy and begin the run. */
    DISPATCH,
    /** Buy one upgrade node. Payload: path ordinal, level. */
    BUY_UPGRADE,
    /** Terminal closed. Drops the server-side session. */
    CLOSE;

    private static final TerminalAction[] VALUES = values();

    public static TerminalAction byId(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : REFRESH;
    }
}
