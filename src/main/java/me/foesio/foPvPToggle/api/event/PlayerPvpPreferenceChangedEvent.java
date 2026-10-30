package me.foesio.foPvPToggle.api.event;

import me.foesio.foPvPToggle.api.PvpChangeCause;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Optional;

/** Fired after an in-memory preference change; database persistence is asynchronous and may race the event. */
public final class PlayerPvpPreferenceChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Optional<Boolean> oldStoredPreference;
    private final boolean oldResolvedPreference;
    private final boolean newPreference;
    private final PvpChangeCause cause;

    public PlayerPvpPreferenceChangedEvent(Player player, Optional<Boolean> oldStoredPreference,
                                           boolean oldResolvedPreference,
                                           boolean newPreference, PvpChangeCause cause, boolean async) {
        super(async);
        this.player = player;
        this.oldStoredPreference = oldStoredPreference;
        this.oldResolvedPreference = oldResolvedPreference;
        this.newPreference = newPreference;
        this.cause = cause;
    }

    public Player getPlayer() { return player; }
    public Optional<Boolean> getOldStoredPreference() { return oldStoredPreference; }
    public boolean getOldResolvedPreference() { return oldResolvedPreference; }
    public boolean getNewPreference() { return newPreference; }
    public PvpChangeCause getCause() { return cause; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
