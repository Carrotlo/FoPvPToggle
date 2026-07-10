package me.foesio.foPvPToggle.region;

import me.foesio.foPvPToggle.FoPvPToggle;

import java.lang.reflect.Constructor;

public final class RegionLookupServiceFactory {
    private RegionLookupServiceFactory() {
    }

    public static RegionLookupService create(FoPvPToggle plugin) {
        if (!isWorldGuardPresent(plugin)) {
            return new UnavailableRegionLookupService();
        }

        try {
            Class<?> serviceClass = Class.forName(
                    "me.foesio.foPvPToggle.region.WorldGuardRegionLookupService",
                    true,
                    plugin.getClass().getClassLoader()
            );
            Constructor<?> constructor = serviceClass.getConstructor(FoPvPToggle.class);
            return (RegionLookupService) constructor.newInstance(plugin);
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning(plugin.plainPrefix()
                    + " WorldGuard region lookup failed to initialize: " + exception.getMessage());
            return new UnavailableRegionLookupService();
        }
    }

    private static boolean isWorldGuardPresent(FoPvPToggle plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            return false;
        }
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard", false, plugin.getClass().getClassLoader());
            Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", false, plugin.getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }
}
