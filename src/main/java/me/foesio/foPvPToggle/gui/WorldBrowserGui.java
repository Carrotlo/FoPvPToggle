package me.foesio.foPvPToggle.gui;

import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.message.FoStyle;
import me.foesio.core.selector.TriStateSelectionHolder;
import me.foesio.core.selector.TriStateSelectionMenus;
import me.foesio.core.selector.TriStateSelectionRequest;
import me.foesio.core.selector.TriStateSelectionState;
import me.foesio.core.selector.TriStateSelections;
import me.foesio.core.selector.WorldSelectionEntries;
import me.foesio.foPvPToggle.FoPvPToggle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Set;

public final class WorldBrowserGui {
    public void open(Player player, FoPvPToggle plugin, int page, String search) {
        TriStateSelectionMenus.open(player, request(plugin, page, search));
    }

    public boolean isBrowser(Inventory inventory) {
        return holder(inventory) != null;
    }

    public TriStateSelectionHolder holder(Inventory inventory) {
        if (inventory.getHolder() instanceof TriStateSelectionHolder holder && holder.request().worldSelection()) {
            return holder;
        }
        return null;
    }

    private TriStateSelectionRequest request(FoPvPToggle plugin, int page, String search) {
        Set<String> disabledWorlds = plugin.toggleService().disabledWorldNames();
        return TriStateSelectionRequest.builder()
                .worldSelection()
                .entries(WorldSelectionEntries.loadedAndConfigured(plugin, disabledWorlds))
                .states(TriStateSelections.fromEnabledDisabled(Set.of(), disabledWorlds))
                .cycleOrder(List.of(TriStateSelectionState.NEUTRAL, TriStateSelectionState.DISABLED))
                .page(page)
                .filter(search)
                .buttons(GuiButtonConfig.defaults())
                .disabledLabel("/pvp disabled")
                .neutralLabel("/pvp active")
                .clickHint("Click to toggle /pvp availability in this world.")
                .emptyTitle("No Worlds Found")
                .emptyLore(List.of(FoStyle.WHITE + "No loaded or configured worlds found."))
                .build();
    }
}
