package me.foesio.foPvPToggle.gui;

import me.foesio.core.editor.EditorItemFactory;
import me.foesio.core.message.FoStyle;
import me.foesio.core.selector.WorldSelectionEntries;
import me.foesio.foPvPToggle.FoPvPToggle;
import me.foesio.core.message.FoMessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EditorGui {
    private final EditorConfig config;
    private final FoMessageService messages;

    public EditorGui(EditorConfig config, FoMessageService messages) {
        this.config = config;
        this.messages = messages;
    }

    public void open(Player player, FoPvPToggle plugin) {
        open(player, plugin, true);
    }

    public void openSilently(Player player, FoPvPToggle plugin) {
        open(player, plugin, false);
    }

    private void open(Player player, FoPvPToggle plugin, boolean playOpenSound) {
        EditorHolder holder = new EditorHolder();
        Inventory inventory = Bukkit.createInventory(holder, config.size(), messages.renderTemplate(config.title(), Map.of()));
        holder.setInventory(inventory);

        fill(inventory);

        setItem(inventory, config.defaultPvp().slot(), buildToggleItem(
                player,
                config.defaultPvp(),
                plugin.getConfig().getBoolean("default-pvp-enabled", true),
                "toggle default PvP"
        ));
        setItem(inventory, config.pvpOverride().slot(), buildToggleItem(
                player,
                config.pvpOverride(),
                plugin.getConfig().getBoolean("pvp-override.enabled", false),
                "toggle the PvP override"
        ));
        setItem(inventory, config.pveProtection().slot(), buildToggleItem(
                player,
                config.pveProtection(),
                plugin.getConfig().getBoolean("pve-protection.enabled", false),
                "toggle PvE protection"
        ));
        setItem(inventory, config.foBossProtection().slot(), buildToggleItem(
                player,
                config.foBossProtection(),
                plugin.getConfig().getBoolean("foboss-protection.enabled", true),
                "toggle FoBoss protection"
        ));
        setItem(inventory, config.disabledRegions().slot(), buildButton(
                player,
                config.disabledRegions(),
                String.valueOf(plugin.toggleService().forcedPvpRegionNames().size()),
                "configure forced PvP regions"
        ));
        Set<String> disabledWorlds = plugin.toggleService().disabledWorldNames();
        int disabledWorldCount = disabledWorlds.size();
        int enabledWorldCount = Math.max(0, WorldSelectionEntries.loadedAndConfigured(plugin, disabledWorlds).size() - disabledWorldCount);
        setItem(inventory, config.disabledWorlds().slot(), EditorItemFactory.worlds(
                player,
                enabledWorldCount,
                disabledWorldCount,
                "enabled"
        ));

        player.openInventory(inventory);
        if (playOpenSound) {
            plugin.editorSounds().open(player);
        }
    }

    private void fill(Inventory inventory) {
        List<Integer> fillerSlots = config.filler().slots();
        if (fillerSlots.isEmpty()) {
            ItemStack filler = EditorItemFactory.filler();
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler.clone());
            }
            return;
        }

        ItemStack filler = buildPlainItem(null, config.filler(), "");
        for (int slot : fillerSlots) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, filler.clone());
            }
        }
    }

    public boolean isEditor(Inventory inventory) {
        return inventory.getHolder() instanceof EditorHolder;
    }

    public boolean isDefaultPvpSlot(int slot) {
        return slot == config.defaultPvp().slot();
    }

    public boolean isPvpOverrideSlot(int slot) {
        return slot == config.pvpOverride().slot();
    }

    public boolean isPveProtectionSlot(int slot) {
        return slot == config.pveProtection().slot();
    }

    public boolean isFoBossProtectionSlot(int slot) {
        return slot == config.foBossProtection().slot();
    }

    public boolean isDisabledRegionsSlot(int slot) {
        return slot == config.disabledRegions().slot();
    }

    public boolean isDisabledWorldsSlot(int slot) {
        return slot == config.disabledWorlds().slot();
    }

    private ItemStack buildPlainItem(Player player, EditorConfig.GuiItem guiItem, String state) {
        return player == null
                ? EditorItemFactory.item(guiItem.material(), name(guiItem, state), lore(guiItem, state))
                : EditorItemFactory.item(player, guiItem.material(), name(player, guiItem, state), lore(player, guiItem, state));
    }

    private ItemStack buildButton(Player player, EditorConfig.GuiItem guiItem, String state, String action) {
        return EditorItemFactory.button(
                player,
                guiItem.material(),
                FoStyle.THEME,
                name(player, guiItem, state),
                buttonInformation(player, guiItem, state, null),
                action
        );
    }

    private ItemStack buildToggleItem(Player player, EditorConfig.GuiItem guiItem, boolean enabled, String action) {
        Material material = guiItem.materialConfigured() ? guiItem.material() : enabled ? Material.LIME_DYE : Material.RED_DYE;
        return EditorItemFactory.button(
                player,
                material,
                enabled ? FoStyle.GOOD : FoStyle.BAD,
                name(player, guiItem, state(enabled)),
                buttonInformation(player, guiItem, state(enabled), enabled),
                action
        );
    }

    private String name(EditorConfig.GuiItem guiItem, String state) {
        return messages.renderTemplate(guiItem.name().replace("{state}", state), Map.of());
    }

    private String name(Player player, EditorConfig.GuiItem guiItem, String state) {
        return messages.renderTemplateForViewer(player, guiItem.name().replace("{state}", state), Map.of());
    }

    private List<String> lore(EditorConfig.GuiItem guiItem, String state) {
        List<String> lore = new ArrayList<>();
        for (String line : guiItem.lore()) {
            lore.add(messages.renderTemplate(line
                    .replace("{state}", state)
                    .replace("{count}", state), Map.of()));
        }
        return lore;
    }

    private List<String> lore(Player player, EditorConfig.GuiItem guiItem, String state) {
        List<String> lore = new ArrayList<>();
        for (String line : guiItem.lore()) {
            lore.add(messages.renderTemplateForViewer(player, line
                    .replace("{state}", state)
                    .replace("{count}", state), Map.of()));
        }
        return lore;
    }

    private List<String> buttonInformation(Player player, EditorConfig.GuiItem guiItem, String state, Boolean enabled) {
        List<String> information = new ArrayList<>();
        if (enabled != null) {
            information.add("State: " + (enabled ? FoStyle.GOOD + "ON" : FoStyle.BAD + "OFF"));
        }
        for (String line : player == null ? lore(guiItem, state) : lore(player, guiItem, state)) {
            String plain = org.bukkit.ChatColor.stripColor(line);
            if (plain == null) {
                plain = line;
            }
            String normalized = plain.trim().toLowerCase(java.util.Locale.ROOT);
            if (normalized.startsWith("current:") || normalized.startsWith("click to")) {
                continue;
            }
            information.add(line);
        }
        return information;
    }

    private void setItem(Inventory inventory, int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    private String state(boolean enabled) {
        return enabled ? "{good}enabled" : "{bad}disabled";
    }

    public static final class EditorHolder implements InventoryHolder {
        private Inventory inventory;

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
