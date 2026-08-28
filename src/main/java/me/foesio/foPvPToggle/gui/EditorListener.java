package me.foesio.foPvPToggle.gui;

import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.editor.EditorDialogInputs;
import me.foesio.core.selector.TriStateSelectionClick;
import me.foesio.core.selector.TriStateSelectionHolder;
import me.foesio.core.selector.TriStateSelectionMenus;
import me.foesio.core.selector.TriStateSelectionRequest;
import me.foesio.core.selector.TriStateSelectionState;
import me.foesio.core.selector.TriStateSelections;
import me.foesio.foPvPToggle.FoPvPToggle;
import me.foesio.foPvPToggle.PvpToggleService;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.sound.FoEditorSounds;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class EditorListener implements Listener {
    private final FoPvPToggle plugin;
    private final EditorGui editorGui;
    private final RegionBrowserGui regionBrowserGui;
    private final WorldBrowserGui worldBrowserGui;
    private final FoMessageService messages;
    private final FoEditorSounds sounds;

    public EditorListener(FoPvPToggle plugin, EditorGui editorGui, RegionBrowserGui regionBrowserGui, WorldBrowserGui worldBrowserGui, FoMessageService messages, FoEditorSounds sounds) {
        this.plugin = plugin;
        this.editorGui = editorGui;
        this.regionBrowserGui = regionBrowserGui;
        this.worldBrowserGui = worldBrowserGui;
        this.messages = messages;
        this.sounds = sounds;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!editorGui.isEditor(top) && !regionBrowserGui.isBrowser(top) && !worldBrowserGui.isBrowser(top)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.hasPermission("fopvptoggle.admin")) {
            messages.send(player, "no-permission", "{prefix}{bad}You do not have permission.");
            sounds.error(player);
            player.closeInventory();
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }

        if (regionBrowserGui.isBrowser(top)) {
            handleRegionBrowserClick(player, rawSlot, top);
            return;
        }
        if (worldBrowserGui.isBrowser(top)) {
            handleWorldBrowserClick(player, rawSlot, top);
            return;
        }

        if (editorGui.isDefaultPvpSlot(rawSlot)) {
            toggleBooleanSetting(player, "default-pvp-enabled", true);
            return;
        }

        if (editorGui.isPvpOverrideSlot(rawSlot)) {
            toggleBooleanSetting(player, "pvp-override.enabled", false);
            return;
        }

        if (editorGui.isPveProtectionSlot(rawSlot)) {
            toggleBooleanSetting(player, "pve-protection.enabled", false);
            return;
        }

        if (editorGui.isFoBossProtectionSlot(rawSlot)) {
            toggleBooleanSetting(player, "foboss-protection.enabled", true);
            return;
        }

        if (editorGui.isDisabledRegionsSlot(rawSlot)) {
            if (!plugin.regionLookupService().available()) {
                messages.send(player, "region-worldguard-missing", "{prefix}{bad}WorldGuard is not installed or not enabled.");
            }
            regionBrowserGui.open(player, plugin, 0, "");
            sounds.open(player);
            messages.send(player, "regions-opened", "{prefix}{good}Region browser opened.");
            return;
        }

        if (editorGui.isDisabledWorldsSlot(rawSlot)) {
            worldBrowserGui.open(player, plugin, 0, "");
            sounds.open(player);
            messages.send(player, "worlds-opened", "{prefix}{good}World selector opened.");
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (editorGui.isEditor(top) || regionBrowserGui.isBrowser(top) || worldBrowserGui.isBrowser(top)) {
            event.setCancelled(true);
        }
    }

    private void handleWorldBrowserClick(Player player, int rawSlot, Inventory top) {
        TriStateSelectionHolder holder = worldBrowserGui.holder(top);
        if (holder == null) {
            return;
        }

        TriStateSelectionClick click = TriStateSelectionMenus.handleClick(rawSlot, holder);
        switch (click.action()) {
            case BACK -> { sounds.back(player); editorGui.openSilently(player, plugin); }
            case PREVIOUS_PAGE -> { sounds.previousPage(player); TriStateSelectionMenus.open(player, click.nextRequest()); }
            case NEXT_PAGE -> { sounds.nextPage(player); TriStateSelectionMenus.open(player, click.nextRequest()); }
            case SEARCH -> { sounds.search(player); openSearch(player, holder.request(), "world", "world", query -> {
                worldBrowserGui.open(player, plugin, 0, query);
                messages.send(player, "world-search-applied", "{prefix}{good}World search applied: {white}{query}", Map.of(
                        "{query}", query
                ));
            }, () -> worldBrowserGui.open(player, plugin, holder.request().page(), holder.request().filter())); }
            case CLEAR_SEARCH -> {
                sounds.clearSearch(player);
                TriStateSelectionMenus.open(player, click.nextRequest());
                messages.send(player, "world-search-cleared", "{prefix}{good}World search cleared.");
            }
            case TOGGLE -> saveDisabledWorldSelection(player, click);
            default -> {
            }
        }
    }

    private void handleRegionBrowserClick(Player player, int rawSlot, Inventory top) {
        TriStateSelectionHolder holder = regionBrowserGui.holder(top);
        if (holder == null) {
            return;
        }

        TriStateSelectionClick click = TriStateSelectionMenus.handleClick(rawSlot, holder);
        switch (click.action()) {
            case BACK -> { sounds.back(player); editorGui.openSilently(player, plugin); }
            case PREVIOUS_PAGE -> { sounds.previousPage(player); TriStateSelectionMenus.open(player, click.nextRequest()); }
            case NEXT_PAGE -> { sounds.nextPage(player); TriStateSelectionMenus.open(player, click.nextRequest()); }
            case SEARCH -> { sounds.search(player); openSearch(player, holder.request(), "region", "spawn", query -> {
                regionBrowserGui.open(player, plugin, 0, query);
                messages.send(player, "region-search-applied", "{prefix}{good}Region search applied: {white}{query}", Map.of(
                        "{query}", query
                ));
            }, () -> regionBrowserGui.open(player, plugin, holder.request().page(), holder.request().filter())); }
            case CLEAR_SEARCH -> {
                sounds.clearSearch(player);
                TriStateSelectionMenus.open(player, click.nextRequest());
                messages.send(player, "region-search-cleared", "{prefix}{good}Region search cleared.");
            }
            case TOGGLE -> saveForcedPvpRegionSelection(player, click);
            default -> {
            }
        }
    }

    private void toggleBooleanSetting(Player player, String path, boolean fallback) {
        boolean enabled = !plugin.getConfig().getBoolean(path, fallback);
        plugin.getConfig().set(path, enabled);
        plugin.saveConfig();
        plugin.reloadRuntimeSettings();
        sounds.toggle(player, enabled);
        messages.send(player, "setting-saved", "{prefix}{good}Saved {white}{setting}{good}: {white}{value}", Map.of(
                "{setting}", path,
                "{value}", enabled ? "enabled" : "disabled"
        ));
        editorGui.openSilently(player, plugin);
    }

    private void saveDisabledWorldSelection(Player player, TriStateSelectionClick click) {
        List<String> saved = normalizedSorted(TriStateSelections.disabledKeys(click.nextRequest()));
        plugin.getConfig().set(PvpToggleService.DISABLED_WORLDS_PATH, saved);
        plugin.saveConfig();
        plugin.reloadRuntimeSettings();
        sounds.cycle(player);

        String normalized = normalize(click.key());
        boolean added = click.newState() == TriStateSelectionState.DISABLED;
        messages.send(player, added ? "world-added" : "world-removed",
                added ? "{prefix}{good}Added disabled world: {white}{world}" : "{prefix}{good}Removed disabled world: {white}{world}",
                Map.of("{world}", normalized));
        worldBrowserGui.open(player, plugin, click.page(), click.filter());
    }

    private void saveForcedPvpRegionSelection(Player player, TriStateSelectionClick click) {
        List<String> saved = normalizedSorted(TriStateSelections.enabledKeys(click.nextRequest()));
        plugin.getConfig().set(PvpToggleService.FORCED_PVP_REGIONS_PATH, saved);
        plugin.getConfig().set("worldguard.disabled-regions", null);
        plugin.saveConfig();
        plugin.reloadRuntimeSettings();
        sounds.cycle(player);

        String normalized = normalize(click.key());
        boolean added = click.newState() == TriStateSelectionState.ENABLED;
        messages.send(player, added ? "region-added" : "region-removed",
                added ? "{prefix}{good}Added forced PvP region: {white}{region}" : "{prefix}{good}Removed forced PvP region: {white}{region}",
                Map.of("{region}", normalized));
        regionBrowserGui.open(player, plugin, click.page(), click.filter());
    }

    private void openSearch(Player player,
                            TriStateSelectionRequest currentRequest,
                            String target,
                            String placeholder,
                            Consumer<String> onSearch,
                            Runnable onCancel) {
        TextDialogRequest request = new TextDialogRequest(
                "Search",
                List.of("Enter a " + target + " search term."),
                "Search",
                currentRequest.filter(),
                placeholder,
                DialogButton.search(),
                DialogButton.cancel(),
                320,
                300,
                64,
                true,
                true,
                false
        );
        boolean openedNative = EditorDialogInputs.openTextFromInventory(
                plugin,
                plugin.core().inventoryCloseSuppressor(),
                plugin.core().dialogService(),
                player,
                request,
                onSearch,
                () -> {
                    messages.send(player, "prompt-cancelled", "{prefix}{bad}Input cancelled.");
                    sounds.back(player);
                    onCancel.run();
                }
        );
        if (openedNative) {
            sounds.open(player);
        }
    }

    private List<String> normalizedSorted(List<String> rawValues) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawValue : rawValues) {
            String value = normalize(rawValue);
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        List<String> saved = new ArrayList<>(normalized);
        saved.sort(String.CASE_INSENSITIVE_ORDER);
        return saved;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
