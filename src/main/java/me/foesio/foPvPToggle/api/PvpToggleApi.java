package me.foesio.foPvPToggle.api;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

/** Public FoPvPToggle contract. Player operations must run on the server thread. */
public interface PvpToggleApi {
    /** Effective PvP state for an online player, including temporary and configured forces. */
    boolean isPvpEnabled(Player player);

    /** Whether FoPvPToggle permits damage between these players; other rules may still deny it. */
    boolean isPvpAllowedByToggle(Player attacker, Player victim);

    /** Whether this player receives FoPvPToggle's configured PvE protection. */
    boolean isPveProtected(Player player);

    /** Explicit stored choice, or empty when the configured default applies. */
    Optional<Boolean> storedPreference(UUID playerId);

    /** Requests a lasting choice change, subject to the same restrictions as /pvp. */
    PvpChangeResult requestPvpChange(Player player, boolean enabled);

    /**
     * Forces PvP on without changing the stored choice. Multiple leases stack.
     * Duration is in server ticks and must be positive. The lease expires or can be released.
     * The owner must be enabled and the player online. Returns an opaque lease ID.
     */
    UUID forcePvp(Player player, Plugin owner, long durationTicks);

    /** Releases one lease owned by the caller. Returns false for an absent or foreign lease. */
    boolean releasePvpForce(Plugin owner, UUID leaseId);
}
