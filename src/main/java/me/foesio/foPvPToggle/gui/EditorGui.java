package me.foesio.foPvPToggle.gui;

import me.foesio.core.editor.EditorItemFactory;
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
        EditorHolder holder = new EditorHolder();
        Inventory inventory = Bukkit.createInventory(holder, config.size(), messages.renderTemplate(config.title(), Map.of()));
        holder.setInventory(inventory);

        fill(inventory);

        setItem(inventory, config.defaultPvp().slot(), buildToggleItem(
                config.defaultPvp(),
                plugin.getConfig().getBoolean("default-pvp-enabled", true)
        ));
        setItem(inventory, config.pvpOverride().slot(), buildToggleItem(
                config.pvpOverride(),
                plugin.getConfig().getBoolean("pvp-override.enabled", false)
        ));
        setItem(inventory, config.pveProtection().slot(), buildToggleItem(
                config.pveProtection(),
                plugin.getConfig().getBoolean("pve-protection.enabled", false)
        ));
        setItem(inventory, config.foBossProtection().slot(), buildToggleItem(
                config.foBossProtection(),
                plugin.getConfig().getBoolean("foboss-protection.enabled", true)
        ));
        setItem(inventory, config.disabledRegions().slot(), buildItem(
                config.disabledRegions(),
                String.valueOf(plugin.toggleService().forcedPvpRegionNames().size())
        ));
        Set<String> disabledWorlds = plugin.toggleService().disabledWorldNames();
        int disabledWorldCount = disabledWorlds.size();
        int enabledWorldCount = Math.max(0, WorldSelectionEntries.loadedAndConfigured(plugin, disabledWorlds).size() - disabledWorldCount);
        setItem(inventory, config.disabledWorlds().slot(), EditorItemFactory.worlds(
                enabledWorldCount,
                disabledWorldCount,
                "enabled"
        ));

        player.openInventory(inventory);
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

        ItemStack filler = buildItem(config.filler(), "");
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

    private ItemStack buildItem(EditorConfig.GuiItem guiItem, String state) {
        return EditorItemFactory.item(guiItem.material(), name(guiItem, state), lore(guiItem, state));
    }

    private ItemStack buildToggleItem(EditorConfig.GuiItem guiItem, boolean enabled) {
        Material material = guiItem.materialConfigured() ? guiItem.material() : enabled ? Material.LIME_DYE : Material.RED_DYE;
        return EditorItemFactory.item(material, name(guiItem, state(enabled)), lore(guiItem, state(enabled)));
    }

    private String name(EditorConfig.GuiItem guiItem, String state) {
        return messages.renderTemplate(guiItem.name().replace("{state}", state), Map.of());
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
