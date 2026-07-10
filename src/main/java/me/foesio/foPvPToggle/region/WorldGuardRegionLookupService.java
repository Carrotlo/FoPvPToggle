package me.foesio.foPvPToggle.region;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import me.foesio.foPvPToggle.FoPvPToggle;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WorldGuardRegionLookupService implements RegionLookupService {
    private final FoPvPToggle plugin;
    private final RegionContainer regionContainer;
    private final AtomicBoolean failureLogged = new AtomicBoolean(false);

    public WorldGuardRegionLookupService(FoPvPToggle plugin) {
        this.plugin = plugin;
        this.regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
    }

    @Override
    public boolean available() {
        return plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard");
    }

    @Override
    public List<String> regionNames() {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try {
            for (World world : plugin.getServer().getWorlds()) {
                RegionManager manager = regionContainer.get(BukkitAdapter.adapt(world));
                if (manager == null) {
                    continue;
                }
                names.addAll(manager.getRegions().keySet());
            }
        } catch (RuntimeException | LinkageError exception) {
            logFailure(exception);
        }
        return new ArrayList<>(names);
    }

    @Override
    public boolean isInAnyRegion(Player player, Set<String> regionNames) {
        if (player == null || regionNames == null || regionNames.isEmpty()) {
            return false;
        }
        return isInAnyRegion(player.getLocation(), regionNames);
    }

    @Override
    public boolean isInAnyRegion(Location location, Set<String> regionNames) {
        if (location == null || regionNames == null || regionNames.isEmpty() || location.getWorld() == null) {
            return false;
        }

        try {
            RegionManager manager = regionContainer.get(BukkitAdapter.adapt(location.getWorld()));
            if (manager == null) {
                return false;
            }

            BlockVector3 block = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            for (String regionName : regionNames) {
                ProtectedRegion region = manager.getRegion(regionName.toLowerCase(Locale.ROOT));
                if (region != null && region.contains(block)) {
                    return true;
                }
            }
        } catch (RuntimeException | LinkageError exception) {
            logFailure(exception);
        }
        return false;
    }

    private void logFailure(Throwable exception) {
        if (failureLogged.compareAndSet(false, true)) {
            plugin.getLogger().warning(plugin.plainPrefix()
                    + " WorldGuard region lookup failed; further failures hidden: " + exception.getMessage());
        }
    }
}
