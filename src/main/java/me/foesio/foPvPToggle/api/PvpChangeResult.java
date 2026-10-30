package me.foesio.foPvPToggle.api;

/** Outcome of a voluntary PvP preference request. */
public enum PvpChangeResult {
    CHANGED,
    UNCHANGED,
    DENIED_OVERRIDE,
    DENIED_WORLD,
    DENIED_REGION,
    DENIED_TEMPORARY_FORCE,
    DENIED_PERMISSION,
    DENIED_OFFLINE,
    DENIED_UNAVAILABLE,
    DENIED_REENTRANT,
    CANCELLED
}
