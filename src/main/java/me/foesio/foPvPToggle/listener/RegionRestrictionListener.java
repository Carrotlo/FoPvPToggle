package me.foesio.foPvPToggle.listener;

import me.foesio.foPvPToggle.PvpToggleService;
import me.foesio.core.message.FoMessageService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RegionRestrictionListener implements Listener {
    private static final long MESSAGE_COOLDOWN_MILLIS = 5000L;

    private final PvpToggleService toggleService;
    private final FoMessageService messages;
    private final Map<UUID, Long> messageCooldowns = new ConcurrentHashMap<>();

    public RegionRestrictionListener(PvpToggleService toggleService, FoMessageService messages) {
        this.toggleService = toggleService;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        enforce(event.getPlayer(), null, event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!changedBlock(event.getFrom(), event.getTo())) {
            return;
        }
        enforce(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        enforce(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        messageCooldowns.remove(event.getPlayer().getUniqueId());
        toggleService.clearCachedRegionRestriction(event.getPlayer().getUniqueId());
    }

    private void enforce(Player player, Location from, Location to) {
        boolean fromRestricted = toggleService.isKnownPvpForcedByRegion(player);
        boolean toRestricted = to != null && toggleService.isPvpForcedByRegion(to);
        toggleService.cacheRegionRestriction(player, toRestricted);
        if (!toRestricted) {
            return;
        }

        toggleService.enforceKnownRegionRestriction(player);
        if (!fromRestricted) {
            sendRegionEntryMessage(player);
        }
    }

    private void sendRegionEntryMessage(Player player) {
        long now = System.currentTimeMillis();
        Long lastSent = messageCooldowns.get(player.getUniqueId());
        if (lastSent != null && now - lastSent < MESSAGE_COOLDOWN_MILLIS) {
            return;
        }
        messageCooldowns.put(player.getUniqueId(), now);
        messages.send(player, "region-pvp-forced-enabled", "{prefix}{good}PvP was forced on for you in this region.");
    }

    private boolean changedBlock(Location from, Location to) {
        if (to == null) {
            return false;
        }
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()
                || from.getWorld() != to.getWorld();
    }
}
