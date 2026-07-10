package me.foesio.foPvPToggle;

import me.foesio.core.FoCoreContext;
import me.foesio.core.logging.FoFileLogger;
import me.foesio.core.storage.WriteBehindStore;
import me.foesio.foPvPToggle.data.UserDataStore;
import me.foesio.foPvPToggle.region.RegionLookupService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PvpToggleService {
    public static final String FORCED_PVP_REGIONS_PATH = "worldguard.forced-pvp-regions.names";
    public static final String DISABLED_WORLDS_PATH = "disabled-worlds.names";
    private static final String LEGACY_DISABLED_REGIONS_PATH = "worldguard.disabled-regions.names";
    private static final long TARGET_CLEAR_COOLDOWN_MILLIS = 2000L;
    private static final double MAX_CLEAR_TARGET_RANGE = 96.0D;

    private final FoPvPToggle plugin;
    private final UserDataStore userDataStore;
    private final FoFileLogger fileLogService;
    private final RegionLookupService regionLookupService;
    private final Map<UUID, Boolean> states;
    private final Map<UUID, Long> targetClearCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> playersInForcedPvpRegion = ConcurrentHashMap.newKeySet();
    private final WriteBehindStore<UUID, Boolean> stateWrites;

    private volatile boolean defaultPvpEnabled = true;
    private volatile boolean pvpOverrideEnabled;
    private volatile boolean pveProtectionEnabled;
    private volatile boolean foBossProtectionEnabled = true;
    private volatile double clearTargetRange = 48.0D;
    private volatile Set<String> forcedPvpRegionNames = Set.of();
    private volatile Set<String> disabledWorldNames = Set.of();

    public PvpToggleService(FoPvPToggle plugin,
                            FoCoreContext core,
                            UserDataStore userDataStore,
                            FoFileLogger fileLogService,
                            RegionLookupService regionLookupService) {
        this.plugin = plugin;
        this.userDataStore = userDataStore;
        this.fileLogService = fileLogService;
        this.regionLookupService = regionLookupService;
        this.states = new ConcurrentHashMap<>(userDataStore.loadStates());
        this.stateWrites = core.writeBehindStore(10L, (playerId, unload) -> states.get(playerId), (playerId, enabled) -> {
            userDataStore.saveState(playerId, enabled);
            return true;
        });
    }

    public void reloadSettings() {
        defaultPvpEnabled = plugin.getConfig().getBoolean("default-pvp-enabled", true);
        pvpOverrideEnabled = plugin.getConfig().getBoolean("pvp-override.enabled", false);
        pveProtectionEnabled = plugin.getConfig().getBoolean("pve-protection.enabled", false);
        foBossProtectionEnabled = plugin.getConfig().getBoolean("foboss-protection.enabled", true);
        clearTargetRange = Math.max(4.0D, Math.min(MAX_CLEAR_TARGET_RANGE, plugin.getConfig().getDouble("pve-protection.clear-target-range", 48.0D)));
        forcedPvpRegionNames = normalizeNames(loadForcedPvpRegionNames());
        disabledWorldNames = normalizeNames(plugin.getConfig().getStringList(DISABLED_WORLDS_PATH));
    }

    public boolean toggle(Player player) {
        if (pvpOverrideEnabled || isPluginDisabledInWorld(player)) {
            return true;
        }
        boolean enabled = !isPvpEnabled(player.getUniqueId());
        setPvpEnabled(player, enabled);
        return enabled;
    }

    public void setPvpEnabled(Player player, boolean enabled) {
        if (pvpOverrideEnabled || isPluginDisabledInWorld(player)) {
            return;
        }
        if (!enabled && isPvpForcedByRegion(player)) {
            enabled = true;
        }
        UUID playerId = player.getUniqueId();
        Boolean previous = states.get(playerId);
        if (previous != null && previous == enabled) {
            return;
        }
        states.put(playerId, enabled);
        stateWrites.writeAsync(playerId, enabled);
        fileLogService.info("PvP state changed for " + player.getName() + " (" + playerId + "): " + (enabled ? "enabled" : "disabled"));
        if (!enabled) {
            clearTargets(player);
        }
    }

    public boolean isPvpEnabled(UUID playerId) {
        if (pvpOverrideEnabled) {
            return true;
        }
        Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
        if (isPluginDisabledInWorld(player)) {
            return true;
        }
        if (playerId == null) {
            return defaultPvpEnabled;
        }
        return states.getOrDefault(playerId, defaultPvpEnabled);
    }

    public boolean isPvpOverrideEnabled() {
        return pvpOverrideEnabled;
    }

    public boolean isPveProtectionEnabled() {
        return pveProtectionEnabled;
    }

    public boolean isFoBossProtectionEnabled() {
        return foBossProtectionEnabled;
    }

    public boolean isFoBossProtected(UUID playerId) {
        return foBossProtectionEnabled && !isEffectivePvpEnabled(playerId);
    }

    public Set<String> forcedPvpRegionNames() {
        return forcedPvpRegionNames;
    }

    public Set<String> disabledWorldNames() {
        return disabledWorldNames;
    }

    public boolean isKnownPvpForcedByRegion(Player player) {
        return player != null && playersInForcedPvpRegion.contains(player.getUniqueId());
    }

    public void cacheRegionRestriction(Player player, boolean forced) {
        if (player == null) {
            return;
        }
        if (forced) {
            playersInForcedPvpRegion.add(player.getUniqueId());
            return;
        }
        playersInForcedPvpRegion.remove(player.getUniqueId());
    }

    public void clearCachedRegionRestriction(UUID playerId) {
        if (playerId == null) {
            return;
        }
        playersInForcedPvpRegion.remove(playerId);
        targetClearCooldowns.remove(playerId);
    }

    public boolean isPvpForcedByRegion(Player player) {
        if (pvpOverrideEnabled || isPluginDisabledInWorld(player)) {
            return false;
        }
        if (forcedPvpRegionNames.isEmpty()) {
            return false;
        }
        return regionLookupService.available() && regionLookupService.isInAnyRegion(player, forcedPvpRegionNames);
    }

    public boolean isPvpForcedByRegion(Location location) {
        if (pvpOverrideEnabled || isPluginDisabledInWorld(location)) {
            return false;
        }
        if (forcedPvpRegionNames.isEmpty()) {
            return false;
        }
        return regionLookupService.available() && regionLookupService.isInAnyRegion(location, forcedPvpRegionNames);
    }

    public boolean enforceRegionRestriction(Player player) {
        return enforceRegionRestriction(player, player == null ? null : player.getLocation());
    }

    public boolean enforceRegionRestriction(Player player, Location location) {
        if (player == null) {
            return false;
        }
        boolean forced = isPvpForcedByRegion(location);
        cacheRegionRestriction(player, forced);
        if (!forced) {
            return false;
        }
        enforceKnownRegionRestriction(player);
        return true;
    }

    public void enforceKnownRegionRestriction(Player player) {
        if (player == null) {
            return;
        }
        if (!isPvpEnabled(player.getUniqueId())) {
            setPvpEnabled(player, true);
        }
    }

    public boolean isPveProtected(Player player) {
        return player != null && pveProtectionEnabled && !isPvpEnabled(player);
    }

    public boolean isPveProtected(UUID playerId) {
        return pveProtectionEnabled && !isEffectivePvpEnabled(playerId);
    }

    public boolean shouldCancelPvp(Player first, Player second) {
        if (first == null || second == null) {
            return false;
        }
        if (isPluginDisabledInWorld(first) || isPluginDisabledInWorld(second)) {
            return false;
        }
        return !isPvpEnabled(first) || !isPvpEnabled(second);
    }

    public boolean shouldCancelPvp(UUID first, UUID second) {
        if (first == null || second == null || first.equals(second)) {
            return false;
        }
        Player firstPlayer = Bukkit.getPlayer(first);
        Player secondPlayer = Bukkit.getPlayer(second);
        if (isPluginDisabledInWorld(firstPlayer) || isPluginDisabledInWorld(secondPlayer)) {
            return false;
        }
        return !isEffectivePvpEnabled(first) || !isEffectivePvpEnabled(second);
    }

    public boolean shouldCancelPlayerToMob(Player player) {
        return isPveProtected(player);
    }

    public boolean shouldCancelMobToPlayer(Player player) {
        return isPveProtected(player);
    }

    public void clearTargets(Player player) {
        if (!pveProtectionEnabled || player == null || !player.isOnline()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastCleared = targetClearCooldowns.get(playerId);
        if (lastCleared != null && now - lastCleared < TARGET_CLEAR_COOLDOWN_MILLIS) {
            return;
        }
        targetClearCooldowns.put(playerId, now);

        for (Entity entity : player.getNearbyEntities(clearTargetRange, clearTargetRange, clearTargetRange)) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            if (mob.getTarget() != null && mob.getTarget().getUniqueId().equals(playerId)) {
                mob.setTarget(null);
            }
        }
    }

    public void enforceOnlineRegionRestrictions() {
        if (pvpOverrideEnabled) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            enforceRegionRestriction(player);
        }
    }

    public boolean isPvpEnabled(Player player) {
        if (player == null) {
            return defaultPvpEnabled;
        }
        return isPluginDisabledInWorld(player) || isKnownPvpForcedByRegion(player) || isPvpEnabled(player.getUniqueId());
    }

    public boolean isPluginDisabledInWorld(Player player) {
        return player != null && isPluginDisabledInWorld(player.getWorld());
    }

    public boolean isPluginDisabledInWorld(Location location) {
        return location != null && isPluginDisabledInWorld(location.getWorld());
    }

    public boolean isPluginDisabledInWorld(World world) {
        return world != null && disabledWorldNames.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    private List<String> loadForcedPvpRegionNames() {
        if (plugin.getConfig().contains(FORCED_PVP_REGIONS_PATH)) {
            return plugin.getConfig().getStringList(FORCED_PVP_REGIONS_PATH);
        }

        List<String> legacyNames = plugin.getConfig().getStringList(LEGACY_DISABLED_REGIONS_PATH);
        if (plugin.getConfig().contains("worldguard.disabled-regions")) {
            plugin.getConfig().set(FORCED_PVP_REGIONS_PATH, legacyNames);
            plugin.getConfig().set("worldguard.disabled-regions", null);
            plugin.saveConfig();
        }
        return legacyNames;
    }

    private boolean isEffectivePvpEnabled(UUID playerId) {
        Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
        return player == null ? isPvpEnabled(playerId) : isPvpEnabled(player);
    }

    public void flushStateWrites() {
        stateWrites.flushSynchronously(List.of(), false);
    }

    private Set<String> normalizeNames(List<String> rawNames) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawName : rawNames) {
            String name = normalizeName(rawName);
            if (!name.isBlank()) {
                normalized.add(name);
            }
        }
        return Set.copyOf(normalized);
    }

    private String normalizeName(String rawName) {
        return rawName == null ? "" : rawName.trim().toLowerCase(Locale.ROOT);
    }
}
