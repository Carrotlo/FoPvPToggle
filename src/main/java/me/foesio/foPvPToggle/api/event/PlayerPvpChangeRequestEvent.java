package me.foesio.foPvPToggle.api.event;

import me.foesio.foPvPToggle.api.PvpChangeCause;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.entity.Player;

import java.util.Optional;

/** Fired before a voluntary, non-no-op preference change. Cancellation prevents all effects. */
public final class PlayerPvpChangeRequestEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Optional<Boolean> oldStoredPreference;
    private final boolean oldResolvedPreference;
    private final boolean requestedPreference;
    private final PvpChangeCause cause;
    private boolean cancelled;

    public PlayerPvpChangeRequestEvent(Player player, Optional<Boolean> oldStoredPreference,
                                       boolean oldResolvedPreference,
                                       boolean requestedPreference, PvpChangeCause cause, boolean async) {
        super(async);
        this.player = player;
        this.oldStoredPreference = oldStoredPreference;
        this.oldResolvedPreference = oldResolvedPreference;
        this.requestedPreference = requestedPreference;
        this.cause = cause;
    }

    public Player getPlayer() { return player; }
    public Optional<Boolean> getOldStoredPreference() { return oldStoredPreference; }
    public boolean getOldResolvedPreference() { return oldResolvedPreference; }
    public boolean getRequestedPreference() { return requestedPreference; }
    public PvpChangeCause getCause() { return cause; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
