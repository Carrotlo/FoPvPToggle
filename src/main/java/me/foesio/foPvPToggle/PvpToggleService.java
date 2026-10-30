package me.foesio.foPvPToggle;

import me.foesio.core.FoCoreContext;
import me.foesio.core.logging.FoFileLogger;
import me.foesio.core.storage.WriteBehindStore;
import me.foesio.foPvPToggle.data.UserDataStore;
import me.foesio.foPvPToggle.api.PvpChangeCause;
import me.foesio.foPvPToggle.api.PvpChangeResult;
import me.foesio.foPvPToggle.api.PvpToggleApi;
import me.foesio.foPvPToggle.api.event.PlayerPvpChangeRequestEvent;
import me.foesio.foPvPToggle.api.event.PlayerPvpPreferenceChangedEvent;
import me.foesio.foPvPToggle.region.RegionLookupService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PvpToggleService implements PvpToggleApi, Listener {
    public static final String FORCED_PVP_REGIONS_PATH = "worldguard.forced-pvp-regions.names";
    public static final String DISABLED_WORLDS_PATH = "disabled-worlds.names";
    private static final String LEGACY_DISABLED_REGIONS_PATH = "worldguard.disabled-regions.names";
    private static final long TARGET_CLEAR_COOLDOWN_MILLIS = 2000L;
    private static final double MAX_CLEAR_TARGET_RANGE = 96.0D;
    private static final long MAX_FORCE_TICKS = 20L * 60L * 60L * 24L;

    private final FoPvPToggle plugin;
    private final UserDataStore userDataStore;
    private final FoFileLogger fileLogService;
    private final RegionLookupService regionLookupService;
    private final Map<UUID, Boolean> states;
    private final Map<UUID, Long> targetClearCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> playersInForcedPvpRegion = ConcurrentHashMap.newKeySet();
    private final WriteBehindStore<UUID, Boolean> stateWrites;
    private final Map<UUID, ForceLease> forceLeases = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> playerForceLeases = new ConcurrentHashMap<>();
    private final Set<UUID> requestsInProgress = new HashSet<>();

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
            return userDataStore.trySaveState(playerId, enabled);
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
        requestPvpChange(player, enabled, PvpChangeCause.COMMAND);
        return isPvpEnabled(player);
    }

    public void setPvpEnabled(Player player, boolean enabled) {
        requestPvpChange(player, enabled);
    }

    @Override
    public PvpChangeResult requestPvpChange(Player player, boolean enabled) {
        return requestPvpChange(player, enabled, PvpChangeCause.API);
    }

    public PvpChangeResult requestPvpChange(Player player, boolean enabled, PvpChangeCause cause) {
        requireServerThread();
        if (player == null || !player.isOnline()) {
            return PvpChangeResult.DENIED_OFFLINE;
        }
        if (!plugin.isEnabled()) {
            return PvpChangeResult.DENIED_UNAVAILABLE;
        }
        if (!player.hasPermission("fopvptoggle.pvptoggle")) {
            return PvpChangeResult.DENIED_PERMISSION;
        }
        if (cause != PvpChangeCause.COMMAND && cause != PvpChangeCause.API) {
            throw new IllegalArgumentException("Only voluntary changes can be requested");
        }
        PvpChangeResult denial = changeDenial(player, enabled);
        if (denial != null) {
            return denial;
        }
        UUID playerId = player.getUniqueId();
        Boolean old = states.get(playerId);
        if (old != null && old == enabled) {
            return PvpChangeResult.UNCHANGED;
        }
        if (!requestsInProgress.add(playerId)) {
            return PvpChangeResult.DENIED_REENTRANT;
        }
        PlayerPvpChangeRequestEvent request = new PlayerPvpChangeRequestEvent(
                player, Optional.ofNullable(old), old == null ? defaultPvpEnabled : old,
                enabled, cause, !Bukkit.isPrimaryThread());
        try {
            Bukkit.getPluginManager().callEvent(request);
        } finally {
            requestsInProgress.remove(playerId);
        }
        if (request.isCancelled()) {
            return PvpChangeResult.CANCELLED;
        }
        if (!plugin.isEnabled()) {
            return PvpChangeResult.DENIED_UNAVAILABLE;
        }
        if (!player.isOnline()) {
            return PvpChangeResult.DENIED_OFFLINE;
        }
        if (!player.hasPermission("fopvptoggle.pvptoggle")) {
            return PvpChangeResult.DENIED_PERMISSION;
        }
        denial = changeDenial(player, enabled);
        if (denial != null) {
            return denial;
        }
        old = states.get(playerId);
        if (old != null && old == enabled) {
            return PvpChangeResult.UNCHANGED;
        }
        applyPreference(player, enabled, cause);
        return PvpChangeResult.CHANGED;
    }

    private PvpChangeResult changeDenial(Player player, boolean enabled) {
        if (pvpOverrideEnabled) {
            return PvpChangeResult.DENIED_OVERRIDE;
        }
        if (isPluginDisabledInWorld(player)) {
            return PvpChangeResult.DENIED_WORLD;
        }
        if (!enabled && isPvpForcedByRegion(player)) {
            return PvpChangeResult.DENIED_REGION;
        }
        if (!enabled && isTemporarilyForced(player.getUniqueId())) {
            return PvpChangeResult.DENIED_TEMPORARY_FORCE;
        }
        return null;
    }

    private void applyPreference(Player player, boolean enabled, PvpChangeCause cause) {
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
        Bukkit.getPluginManager().callEvent(new PlayerPvpPreferenceChangedEvent(
                player, Optional.ofNullable(previous), previous == null ? defaultPvpEnabled : previous,
                enabled, cause, !Bukkit.isPrimaryThread()));
    }

    public boolean isPvpEnabled(UUID playerId) {
        if (pvpOverrideEnabled) {
            return true;
        }
        Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
        if (isPluginDisabledInWorld(player)) {
            return true;
        }
        if (playerId != null && isTemporarilyForced(playerId)) {
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
        if (!states.getOrDefault(player.getUniqueId(), defaultPvpEnabled)) {
            applyPreference(player, true, PvpChangeCause.REGION_ENFORCEMENT);
        }
    }

    public boolean isPveProtected(Player player) {
        return player != null && pveProtectionEnabled && !isPvpEnabled(player);
    }

    @Override
    public Optional<Boolean> storedPreference(UUID playerId) {
        return Optional.ofNullable(playerId == null ? null : states.get(playerId));
    }

    @Override
    public boolean isPvpAllowedByToggle(Player attacker, Player victim) {
        return attacker != null && victim != null && attacker.isOnline() && victim.isOnline()
                && !attacker.getUniqueId().equals(victim.getUniqueId())
                && !shouldCancelPvp(attacker, victim);
    }

    @Override
    public UUID forcePvp(Player player, Plugin owner, long durationTicks) {
        requireServerThread();
        if (!plugin.isEnabled() || player == null || !player.isOnline() || owner == null || !owner.isEnabled()) {
            throw new IllegalArgumentException("Provider, owner, and player must be active");
        }
        if (durationTicks <= 0 || durationTicks > MAX_FORCE_TICKS) {
            throw new IllegalArgumentException("Duration must be 1 to " + MAX_FORCE_TICKS + " ticks");
        }
        UUID leaseId = UUID.randomUUID();
        BukkitTask expiry = Bukkit.getScheduler().runTaskLater(plugin, () -> removeForceLease(leaseId), durationTicks);
        forceLeases.put(leaseId, new ForceLease(player.getUniqueId(), owner, expiry));
        playerForceLeases.computeIfAbsent(player.getUniqueId(), unused -> ConcurrentHashMap.newKeySet()).add(leaseId);
        return leaseId;
    }

    @Override
    public boolean releasePvpForce(Plugin owner, UUID leaseId) {
        requireServerThread();
        if (owner == null || leaseId == null) {
            return false;
        }
        ForceLease lease = forceLeases.get(leaseId);
        if (lease == null || lease.owner() != owner) {
            return false;
        }
        removeForceLease(leaseId);
        return true;
    }

    private boolean isTemporarilyForced(UUID playerId) {
        Set<UUID> leases = playerForceLeases.get(playerId);
        return leases != null && !leases.isEmpty();
    }

    private void removeForceLease(UUID leaseId) {
        ForceLease lease = forceLeases.remove(leaseId);
        if (lease == null) {
            return;
        }
        lease.expiry().cancel();
        playerForceLeases.computeIfPresent(lease.playerId(), (ignored, leases) -> {
            leases.remove(leaseId);
            return leases.isEmpty() ? null : leases;
        });
        Player player = Bukkit.getPlayer(lease.playerId());
        if (player != null && isPveProtected(player)) {
            clearTargets(player, true);
        }
    }

    @EventHandler
    public void onOwnerDisable(PluginDisableEvent event) {
        forceLeases.entrySet().stream()
                .filter(entry -> entry.getValue().owner() == event.getPlugin())
                .map(Map.Entry::getKey).toList().forEach(this::removeForceLease);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Set<UUID> leases = playerForceLeases.get(event.getPlayer().getUniqueId());
        if (leases != null) {
            Set.copyOf(leases).forEach(this::removeForceLease);
        }
    }

    public void clearTemporaryForces() {
        Set.copyOf(forceLeases.keySet()).forEach(this::removeForceLease);
    }

    private void requireServerThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("FoPvPToggle player operations require the primary server thread");
        }
    }

    private record ForceLease(UUID playerId, Plugin owner, BukkitTask expiry) { }

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
        clearTargets(player, false);
    }

    private void clearTargets(Player player, boolean bypassCooldown) {
        if (!pveProtectionEnabled || player == null || !player.isOnline()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastCleared = targetClearCooldowns.get(playerId);
        if (!bypassCooldown && lastCleared != null && now - lastCleared < TARGET_CLEAR_COOLDOWN_MILLIS) {
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
