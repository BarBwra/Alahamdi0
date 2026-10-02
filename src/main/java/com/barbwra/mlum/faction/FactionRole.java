package com.barbwra.mlum.faction;

/**
 * The five ranks inside a faction, highest first.
 *
 * <p><b>Ordinal is the authority.</b> Every permission question in the faction system reduces to
 * "is this rank at least that rank", so the checks are comparisons against {@link #ordinal()} rather
 * than a table of booleans per rank. Adding a sixth rank later means inserting it in the right place
 * in this list and nothing else - which is the whole reason the permissions are not stored per role
 * as flags.</p>
 *
 * <p><b>Names are stored as the enum, displayed as Arabic.</b> The saved data holds {@code LEADER},
 * never the Arabic string, so renaming a rank is a display change and never a migration.</p>
 */
public enum FactionRole {

    /** Founder. The only rank that can disband, promote to deputy, or set vault page access. */
    LEADER("القائد"),

    /** Deputy. Can spend the faction bank and manage everyone below. */
    DEPUTY("النائب"),

    /** Officer. Can invite and kick members, but cannot touch the bank. */
    OFFICER("المسؤول"),

    /** Ordinary member. */
    MEMBER("عضو"),

    /** Lowest rank. Joins with this by default; sees what it is granted and nothing more. */
    GUEST("ضيف");

    private final String arabic;

    FactionRole(String arabic) {
        this.arabic = arabic;
    }

    /** The label shown in the members list, in logical order - display shaping happens at draw. */
    public String arabic() {
        return arabic;
    }

    /** The rank a newly joined player gets. */
    public static FactionRole defaultRole() {
        return GUEST;
    }

    /** True when this rank sits at or above {@code other} in the hierarchy. */
    public boolean atLeast(FactionRole other) {
        return ordinal() <= other.ordinal();
    }

    /** True when this rank outranks {@code other} strictly - the test for kick and promote. */
    public boolean outranks(FactionRole other) {
        return ordinal() < other.ordinal();
    }

    /**
     * Whether this rank may spend the faction's money.
     *
     * <p>Deliberately the top two only, as specified: anyone can donate, but buying a vault page is
     * a decision the faction cannot undo for 24 hours, so it belongs to leadership.</p>
     */
    public boolean canSpendBank() {
        return atLeast(DEPUTY);
    }

    /** Whether this rank may decide which ranks can reach which vault page. */
    public boolean canSetVaultAccess() {
        return this == LEADER;
    }

    /** Whether this rank may invite and kick. */
    public boolean canManageMembers() {
        return atLeast(OFFICER);
    }

    /** Parses a stored name, falling back to the joining rank rather than throwing on bad data. */
    public static FactionRole byName(String name) {
        for (FactionRole role : values()) {
            if (role.name().equalsIgnoreCase(name)) {
                return role;
            }
        }
        return defaultRole();
    }
}
