package me.foesio.foPvPToggle.api;

/** Source of a preference change. Region enforcement is not a voluntary request. */
public enum PvpChangeCause {
    COMMAND,
    API,
    REGION_ENFORCEMENT
}
