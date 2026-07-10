package me.foesio.foPvPToggle.region;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

final class UnavailableRegionLookupService implements RegionLookupService {
    @Override
    public boolean available() {
        return false;
    }

    @Override
    public List<String> regionNames() {
        return List.of();
    }

    @Override
    public boolean isInAnyRegion(Player player, Set<String> regionNames) {
        return false;
    }

    @Override
    public boolean isInAnyRegion(Location location, Set<String> regionNames) {
        return false;
    }
}
