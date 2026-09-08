package me.foesio.foPvPToggle.gui;

import me.foesio.core.gui.GuiTitles;
import me.foesio.core.logging.FoFileLogger;
import me.foesio.foPvPToggle.FoPvPToggle;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class EditorConfig {
    private final FoPvPToggle plugin;
    private final FoFileLogger fileLogService;

    private String title;
    private int rows;
    private GuiItem filler;
    private GuiItem defaultPvp;
    private GuiItem pvpOverride;
    private GuiItem foBossProtection;
    private GuiItem pveProtection;
    private GuiItem disabledRegions;
    private GuiItem disabledWorlds;

    public EditorConfig(FoPvPToggle plugin, FoFileLogger fileLogService) {
        this.plugin = plugin;
        this.fileLogService = fileLogService;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "guis/editor.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        if (file.exists()) {
            try {
                yaml.load(file);
            } catch (IOException | InvalidConfigurationException exception) {
                plugin.getLogger().warning(plugin.plainPrefix() + " Failed to load guis/editor.yml: " + exception.getMessage());
                fileLogService.warn("Failed to load guis/editor.yml: " + exception.getMessage());
            }
        }
        migrateDefaultSlots(yaml, file);

        title = yaml.getString("title", "&8" + GuiTitles.smallCaps("FoPvPToggle Editor"));
        rows = Math.max(1, Math.min(6, yaml.getInt("rows", 3)));
        filler = readItem(yaml, "filler", Material.GRAY_STAINED_GLASS_PANE, -1, " ", List.of());
        defaultPvp = readItem(yaml, "default-pvp", Material.LIME_DYE, 10, "{theme}Default PvP",
                List.of("{white}Current: {state}", "{muted}New players start with this state.", "{muted}Click to toggle."));
        pvpOverride = readItem(yaml, "pvp-override", Material.REDSTONE_TORCH, 11, "{bad}Admin PvP Override",
                List.of("{white}Current: {state}", "{muted}Forces PvP on for everyone.", "{muted}Blocks /pvp abuse during events.", "{muted}Click to toggle."));
        foBossProtection = readItem(yaml, "foboss-protection", Material.SHIELD, 12, "{theme}FoBoss Protection",
                List.of("{white}Current: {state}", "{muted}Stops FoBoss targeting and damaging", "{muted}players with /pvp disabled.", "{muted}Click to toggle."));
        pveProtection = readItem(yaml, "pve-protection", Material.IRON_SWORD, 14, "{theme}PvE Protection",
                List.of("{white}Current: {state}", "{muted}Blocks mob damage both ways", "{muted}while /pvp is disabled.", "{muted}Click to toggle."));
        disabledRegions = readItem(yaml, "forced-pvp-regions", Material.MAP, 13, "{theme}Forced PvP Regions",
                List.of("{white}Configured: {count}", "{muted}WorldGuard regions that force PvP on.", "{muted}Click to manage."));
        disabledWorlds = readItem(yaml, "disabled-worlds", Material.GRASS_BLOCK, 15, "{theme}World Rules",
                List.of("{white}Disabled worlds: {count}", "{muted}The plugin leaves PvP alone there.", "{muted}Click to manage worlds."));
    }

    public String title() {
        return title;
    }

    public int size() {
        return rows * 9;
    }

    public GuiItem filler() {
        return filler;
    }

    public GuiItem defaultPvp() {
        return defaultPvp;
    }

    public GuiItem pvpOverride() {
        return pvpOverride;
    }

    public GuiItem foBossProtection() {
        return foBossProtection;
    }

    public GuiItem pveProtection() {
        return pveProtection;
    }

    public GuiItem disabledRegions() {
        return disabledRegions;
    }

    public GuiItem disabledWorlds() {
        return disabledWorlds;
    }

    private GuiItem readItem(YamlConfiguration yaml, String key, Material fallbackMaterial, int fallbackSlot, String fallbackName, List<String> fallbackLore) {
        String base = "items." + key + ".";
        String rawMaterial = yaml.getString(base + "material");
        Material material = parseMaterial(rawMaterial, fallbackMaterial, base + "material");
        boolean materialConfigured = isValidMaterial(rawMaterial);
        int slot = yaml.getInt(base + "slot", fallbackSlot);
        String name = yaml.getString(base + "name", fallbackName);
        List<String> lore = yaml.contains(base + "lore") ? yaml.getStringList(base + "lore") : fallbackLore;
        List<Integer> slots = yaml.getIntegerList(base + "slots");
        return new GuiItem(material, materialConfigured, slot, name, lore, slots);
    }

    private Material parseMaterial(String raw, Material fallback, String path) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw.trim());
        if (material == null || material == Material.AIR) {
            plugin.getLogger().warning(plugin.plainPrefix() + " Invalid material in guis/editor.yml at " + path + ": " + raw);
            fileLogService.warn("Invalid material in guis/editor.yml at " + path + ": " + raw);
            return fallback;
        }
        return material;
    }

    private boolean isValidMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        Material material = Material.matchMaterial(raw.trim());
        return material != null && material != Material.AIR;
    }

    private void migrateDefaultSlots(YamlConfiguration yaml, File file) {
        if (!file.exists()) {
            return;
        }

        boolean changed = false;
        changed |= migrateSlot(yaml, "default-pvp", 11, 10);
        changed |= migrateSlot(yaml, "pvp-override", 12, 11);
        changed |= migrateSlot(yaml, "foboss-protection", 13, 12);
        changed |= migrateSlot(yaml, "forced-pvp-regions", 14, 13);
        changed |= migrateSlot(yaml, "pve-protection", 15, 14);
        changed |= migrateSlot(yaml, "disabled-worlds", 16, 15);
        if (!changed) {
            return;
        }

        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning(plugin.plainPrefix() + " Failed to migrate default editor slots: " + exception.getMessage());
            fileLogService.warn("Failed to migrate default editor slots: " + exception.getMessage());
        }
    }

    private boolean migrateSlot(YamlConfiguration yaml, String key, int oldSlot, int newSlot) {
        String path = "items." + key + ".slot";
        if (!yaml.contains(path) || yaml.getInt(path) != oldSlot) {
            return false;
        }
        yaml.set(path, newSlot);
        return true;
    }

    public record GuiItem(Material material, boolean materialConfigured, int slot, String name, List<String> lore, List<Integer> slots) {
        public GuiItem {
            lore = List.copyOf(new ArrayList<>(lore == null ? List.of() : lore));
            slots = List.copyOf(new ArrayList<>(slots == null ? List.of() : slots));
        }
    }
}
