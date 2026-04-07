package de.swarp.model;

/**
 * Available warp categories players can assign to their warp.
 */
public enum WarpCategory {
    SHOP    ("🛒", "Shop"),
    FARM    ("🌾", "Farm"),
    PVP     ("⚔", "PvP"),
    BASE    ("🏠", "Base"),
    PUBLIC  ("🌍", "Public"),
    OTHER   ("📌", "Other");

    public final String icon;
    public final String displayName;

    WarpCategory(String icon, String displayName) {
        this.icon = icon;
        this.displayName = displayName;
    }

    /** Parse from string, fallback to OTHER */
    public static WarpCategory fromString(String s) {
        if (s == null) return OTHER;
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
