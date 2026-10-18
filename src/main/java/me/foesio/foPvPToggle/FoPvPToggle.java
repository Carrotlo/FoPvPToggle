package me.foesio.foPvPToggle;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.dialog.NativeDialogConfigDefaults;
import me.foesio.core.logging.FoFileLogger;
import me.foesio.core.message.FoMessageMigrations;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foPvPToggle.command.AdminCommand;
import me.foesio.foPvPToggle.command.ToggleCommand;
import me.foesio.foPvPToggle.data.UserDataStore;
import me.foesio.foPvPToggle.gui.EditorConfig;
import me.foesio.foPvPToggle.gui.EditorGui;
import me.foesio.foPvPToggle.gui.EditorListener;
import me.foesio.foPvPToggle.gui.RegionBrowserGui;
import me.foesio.foPvPToggle.gui.WorldBrowserGui;
import me.foesio.foPvPToggle.listener.CombatListener;
import me.foesio.foPvPToggle.listener.RegionRestrictionListener;
import me.foesio.foPvPToggle.region.RegionLookupService;
import me.foesio.foPvPToggle.region.RegionLookupServiceFactory;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class FoPvPToggle extends JavaPlugin {
    private static final String MODRINTH_PROJECT_ID = "fopvptoggle";
    private static final int BSTATS_PLUGIN_ID = 32426;

    private FoCoreContext core;
    private FoMessageService messages;
    private FoFileLogger fileLogService;
    private UserDataStore userDataStore;
    private PvpToggleService toggleService;
    private EditorConfig editorConfig;
    private RegionLookupService regionLookupService;
    private FoSoundService sounds;
    private FoAdminSounds adminSounds;
    private FoEditorSounds editorSounds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        NativeDialogConfigDefaults.addDefaults(getConfig());
        saveConfig();

        fileLogService = FoFileLogger.create(this);
        fileLogService.configureFromConfig("file-logging", true);
        messages = FoMessageService.load(this, messageMigrations());
        core = FoPluginCore.create(this);
        migrateSprites();
        core.warnIfNativeDialogsUnavailable();
        core.metrics(BSTATS_PLUGIN_ID);
        editorConfig = new EditorConfig(this, fileLogService);
        editorConfig.reload();
        sounds = core.createSounds();
        adminSounds = FoAdminSounds.create(sounds);
        editorSounds = FoEditorSounds.create(sounds);

        try {
            userDataStore = new UserDataStore(this, fileLogService);
            userDataStore.initialize();
        } catch (RuntimeException exception) {
            prefixedSevere("Failed to initialize userdata.db: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        regionLookupService = RegionLookupServiceFactory.create(this);
        toggleService = new PvpToggleService(this, core, userDataStore, fileLogService, regionLookupService);
        toggleService.reloadSettings();
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            core.placeholders("fopvptoggle")
                    .offline("status", player -> isPvpEnabled(player.getUniqueId()) ? "enabled" : "disabled")
                    .registerIfAvailable();
        }
        UpdateNoticeService updateService = core.createUpdateNotices(messages, MODRINTH_PROJECT_ID, adminSounds).start();

        EditorGui editorGui = new EditorGui(editorConfig, messages);
        RegionBrowserGui regionBrowserGui = new RegionBrowserGui();
        WorldBrowserGui worldBrowserGui = new WorldBrowserGui();

        getServer().getPluginManager().registerEvents(new CombatListener(toggleService, messages), this);
        getServer().getPluginManager().registerEvents(new RegionRestrictionListener(toggleService, messages, sounds), this);
        getServer().getPluginManager().registerEvents(new EditorListener(this, editorGui, regionBrowserGui, worldBrowserGui, messages, editorSounds), this);

        registerCommands(updateService, editorGui);
        prefixedInfo("Enabled.");
        fileLogService.info("Enabled.");
    }

    @Override
    public void onDisable() {
        if (fileLogService != null) {
            fileLogService.info("Disabled.");
        }
        if (userDataStore != null) {
            if (toggleService != null) {
                toggleService.flushStateWrites();
            }
            userDataStore.close();
        }
        if (fileLogService != null) {
            fileLogService.shutdown();
        }
        if (core != null) {
            core.close();
            core = null;
        }
    }

    public FoReloadResult reloadRuntimeSettings() {
        return FoReloadRegistry.create()
                .add("config", () -> {
                    reloadConfig();
                    NativeDialogConfigDefaults.addDefaults(getConfig());
                    saveConfig();
                    refreshCore();
                })
                .add("messages", messages::reload)
                .add("sounds", sounds::reload)
                .add("editor", editorConfig::reload)
                .add("pvp-settings", () -> {
                    toggleService.reloadSettings();
                    toggleService.enforceOnlineRegionRestrictions();
                })
                .add("file-logging", () -> {
                    fileLogService.configureFromConfig("file-logging", false);
                    fileLogService.info("Reloaded config, messages, and editor settings.");
                })
                .reload();
    }

    public boolean isPvpEnabled(UUID playerId) {
        return toggleService == null || toggleService.isPvpEnabled(playerId);
    }

    public boolean isPvpDisabled(UUID playerId) {
        return !isPvpEnabled(playerId);
    }

    public boolean isPvpOverrideEnabled() {
        return toggleService != null && toggleService.isPvpOverrideEnabled();
    }

    public boolean isPveProtectionEnabled() {
        return toggleService != null && toggleService.isPveProtectionEnabled();
    }

    public boolean isPveProtected(UUID playerId) {
        return toggleService != null && toggleService.isPveProtected(playerId);
    }

    public boolean isFoBossProtectionEnabled() {
        return toggleService != null && toggleService.isFoBossProtectionEnabled();
    }

    public boolean isFoBossProtected(UUID playerId) {
        return toggleService != null && toggleService.isFoBossProtected(playerId);
    }

    public FoCoreContext core() {
        return core;
    }

    public FoSoundService sounds() {
        return sounds;
    }

    public FoAdminSounds adminSounds() {
        return adminSounds;
    }

    public FoEditorSounds editorSounds() {
        return editorSounds;
    }

    public PvpToggleService toggleService() {
        return toggleService;
    }

    public RegionLookupService regionLookupService() {
        return regionLookupService;
    }

    private void registerCommands(UpdateNoticeService updateService, EditorGui editorGui) {
        PluginCommand toggleCommand = getCommand("fopvptoggle");
        if (toggleCommand == null) {
            prefixedSevere("Command 'fopvptoggle' is missing in plugin.yml.");
        } else {
            ToggleCommand command = new ToggleCommand(toggleService, messages, sounds, adminSounds);
            toggleCommand.setExecutor(command);
            toggleCommand.setTabCompleter(command);
        }

        PluginCommand adminCommand = getCommand("fopvptoggleadmin");
        if (adminCommand == null) {
            prefixedSevere("Command 'fopvptoggleadmin' is missing in plugin.yml.");
        } else {
            AdminCommand command = new AdminCommand(this, messages, updateService, editorGui);
            adminCommand.setExecutor(command);
            adminCommand.setTabCompleter(command);
        }
    }

    private void prefixedInfo(String message) {
        getLogger().info(plainPrefix() + " " + message);
    }

    private void prefixedSevere(String message) {
        getLogger().severe(plainPrefix() + " " + message);
    }

    public String plainPrefix() {
        return "[" + getName() + "]";
    }

    private void refreshCore() {
        if (core != null) {
            core.close();
        }
        core = FoPluginCore.create(this);
        core.warnIfNativeDialogsUnavailable();
        core.metrics(BSTATS_PLUGIN_ID);
        sounds.reload();
    }

    private void migrateSprites() {
        messages.migrateToVersion(core.migrations(), 1, config -> {
            boolean changed = false;
            changed |= FoMessageService.addMissingToken(config, "tokens.prefix", ":iron_sword:", null);
            changed |= FoMessageService.addMissingToken(config, "reload-success", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "reload-failed", ":redstone:");
            changed |= FoMessageService.addMissingToken(config, "editor-opened", ":book:");
            changed |= FoMessageService.addMissingToken(config, "setting-saved", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "worlds-opened", ":grass_block:");
            changed |= FoMessageService.addMissingToken(config, "world-added", ":grass_block:");
            changed |= FoMessageService.addMissingToken(config, "world-removed", ":barrier:");
            changed |= FoMessageService.addMissingToken(config, "regions-opened", ":map:");
            changed |= FoMessageService.addMissingToken(config, "region-added", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "region-removed", ":barrier:");
            return changed;
        });
    }

    private FoMessageMigrations messageMigrations() {
        return FoMessageMigrations.create()
                .replaceInString("tokens.prefix", "{separator}", "&8»")
                .remove("tokens.yellow")
                .remove("tokens.separator")
                .replaceExact("damage-blocked",
                        "{bad}Damage blocked.",
                        "{prefix}{bad}Damage blocked because /pvp is disabled.")
                .replaceExact("region-added",
                        "{prefix}{good}Added disabled region: {white}{region}",
                        "{prefix}{good}Added forced PvP region: {white}{region}")
                .replaceExact("region-removed",
                        "{prefix}{good}Removed disabled region: {white}{region}",
                        "{prefix}{good}Removed forced PvP region: {white}{region}")
                .replaceExact("region-toggle-denied",
                        "{prefix}{bad}You cannot enable /pvp inside this WorldGuard region.",
                        "{prefix}{bad}You cannot disable /pvp inside this WorldGuard region.")
                .removeExact("region-pvp-forced-disabled",
                        "{prefix}{bad}PvP was forced off for you in this region.",
                        "{prefix}{good}PvP was forced on for you in this region.")
                .removeExact("region-search-prompt",
                        "{prefix}{muted}Enter a region search term. Type {white}cancel{muted} to abort.")
                .replaceExact("reload-success",
                        "{prefix}{good}Config, messages, and GUI files reloaded.",
                        "{prefix}{good}Config, messages, and editor settings reloaded.")
                .removeIfMemorySectionString("general")
                .removeIfMemorySectionString("admin")
                .removeIfMemorySectionString("native-dialogs")
                .removeIfMemorySectionString("editor")
                .prefixTopLevelStrings("{prefix}", java.util.Set.of("tokens"))
                .build();
    }
}
