package me.foesio.foPvPToggle.gui;

import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.message.FoStyle;
import me.foesio.core.selector.StringSelectionEntries;
import me.foesio.core.selector.TriStateSelectionHolder;
import me.foesio.core.selector.TriStateSelectionMenus;
import me.foesio.core.selector.TriStateSelectionRequest;
import me.foesio.core.selector.TriStateSelectionState;
import me.foesio.core.selector.TriStateSelections;
import me.foesio.foPvPToggle.FoPvPToggle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Set;

public final class RegionBrowserGui {
    private static final String TITLE = "Forced PvP Regions";

    public void open(Player player, FoPvPToggle plugin, int page, String search) {
        TriStateSelectionMenus.open(player, request(plugin, page, search));
    }

    public boolean isBrowser(Inventory inventory) {
        return holder(inventory) != null;
    }

    public TriStateSelectionHolder holder(Inventory inventory) {
        if (inventory.getHolder() instanceof TriStateSelectionHolder holder
                && !holder.request().worldSelection()
                && TITLE.equals(holder.request().title())) {
            return holder;
        }
        return null;
    }

    private TriStateSelectionRequest request(FoPvPToggle plugin, int page, String search) {
        Set<String> forcedRegions = plugin.toggleService().forcedPvpRegionNames();
        boolean worldGuardAvailable = plugin.regionLookupService().available();
        List<String> regionNames = worldGuardAvailable ? plugin.regionLookupService().regionNames() : List.of();
        return TriStateSelectionRequest.builder()
                .title(TITLE)
                .entries(StringSelectionEntries.fromKeys(regionNames, forcedRegions))
                .states(TriStateSelections.fromEnabledDisabled(forcedRegions, Set.of()))
                .cycleOrder(List.of(TriStateSelectionState.NEUTRAL, TriStateSelectionState.ENABLED))
                .page(page)
                .filter(search)
                .buttons(GuiButtonConfig.defaults())
                .enabledLabel("forced on")
                .neutralLabel("normal")
                .clickHint("Click to toggle forced PvP.")
                .emptyTitle(worldGuardAvailable ? "No Regions Found" : "WorldGuard Unavailable")
                .emptyLore(worldGuardAvailable
                        ? List.of(FoStyle.WHITE + "Create WorldGuard regions first.")
                        : List.of(FoStyle.WHITE + "Install and enable WorldGuard", FoStyle.WHITE + "to browse region names."))
                .build();
    }
}
