package me.foesio.foPvPToggle.region;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

public interface RegionLookupService {
    boolean available();

    List<String> regionNames();

    boolean isInAnyRegion(Player player, Set<String> regionNames);

    boolean isInAnyRegion(Location location, Set<String> regionNames);
}
