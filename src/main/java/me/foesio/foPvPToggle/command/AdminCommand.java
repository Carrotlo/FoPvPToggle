package me.foesio.foPvPToggle.command;

import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foPvPToggle.FoPvPToggle;
import me.foesio.foPvPToggle.gui.EditorGui;
import me.foesio.core.message.FoMessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdminCommand implements CommandExecutor, TabCompleter {
    private final FoPvPToggle plugin;
    private final FoMessageService messages;
    private final UpdateNoticeService updateService;
    private final EditorGui editorGui;

    public AdminCommand(FoPvPToggle plugin, FoMessageService messages, UpdateNoticeService updateService, EditorGui editorGui) {
        this.plugin = plugin;
        this.messages = messages;
        this.updateService = updateService;
        this.editorGui = editorGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("fopvptoggle.admin")) {
            messages.send(sender, "no-permission", "{prefix}{bad}You do not have permission.");
            plugin.adminSounds().updateError(sender);
            return true;
        }
        if (args.length != 1) {
            messages.send(sender, "usage-admin", "{prefix}{muted}Usage: {white}/{label} <version|reload|editor>", Map.of("{label}", label));
            plugin.adminSounds().updateError(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "version" -> handleVersion(sender);
            case "reload" -> handleReload(sender);
            case "editor" -> handleEditor(sender);
            default -> {
                messages.send(sender, "usage-admin", "{prefix}{muted}Usage: {white}/{label} <version|reload|editor>", Map.of("{label}", label));
                plugin.adminSounds().updateError(sender);
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("fopvptoggle.admin") || args.length != 1) {
            return Collections.emptyList();
        }
        return partial(List.of("version", "reload", "editor"), args[0]);
    }

    private boolean handleVersion(CommandSender sender) {
        messages.send(sender, "version-author", "{prefix}{muted}Author: {white}Carrotio");
        messages.send(sender, "version-running", "{prefix}{muted}Running version: {white}{version}", Map.of("{version}", plugin.getDescription().getVersion()));
        updateService.sendVersion(sender);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        FoReloadResult result = plugin.reloadRuntimeSettings();
        if (!result.successful()) {
            if (sender instanceof Player player) {
                plugin.adminSounds().reloadError(player);
            }
            messages.send(sender, "reload-failed", "{prefix}{bad}Reload failed at {white}{step}{bad}: {muted}{error}", Map.of(
                    "{step}", result.failedStep(),
                    "{error}", result.errorMessage()
            ));
            return true;
        }
        if (sender instanceof Player player) {
            plugin.adminSounds().reload(player);
        }
        messages.send(sender, "reload-success", "{prefix}{good}Config, messages, and editor settings reloaded.");
        return true;
    }

    private boolean handleEditor(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only", "{prefix}{bad}This command is in-game only.");
            return true;
        }
        editorGui.open(player, plugin);
        messages.send(player, "editor-opened", "{prefix}{good}Editor opened.");
        return true;
    }

    private List<String> partial(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
