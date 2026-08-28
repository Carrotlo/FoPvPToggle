package me.foesio.foPvPToggle.command;

import me.foesio.foPvPToggle.PvpToggleService;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.sound.FoAdminSounds;
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

public final class ToggleCommand implements CommandExecutor, TabCompleter {
    private final PvpToggleService toggleService;
    private final FoMessageService messages;
    private final FoSoundService sounds;
    private final FoAdminSounds adminSounds;

    public ToggleCommand(PvpToggleService toggleService, FoMessageService messages, FoSoundService sounds, FoAdminSounds adminSounds) {
        this.toggleService = toggleService;
        this.messages = messages;
        this.sounds = sounds;
        this.adminSounds = adminSounds;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("fopvptoggle.pvptoggle")) {
            messages.send(sender, "no-permission", "{prefix}{bad}You do not have permission.");
            adminSounds.updateError(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only", "{prefix}{bad}This command is in-game only.");
            return true;
        }

        if (args.length > 1) {
            messages.send(sender, "usage-toggle", "{prefix}{muted}Usage: {white}/{label} [on|off|status]", Map.of("{label}", label));
            adminSounds.updateError(sender);
            return true;
        }

        if (toggleService.isPluginDisabledInWorld(player)) {
            adminSounds.updateError(player);
            messages.send(player, "pvp-world-disabled", "{prefix}{bad}/pvp is disabled in this world. PvP is always enabled here.");
            return true;
        }

        if (args.length == 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "on", "enable", "enabled" -> {
                    if (toggleService.isPvpOverrideEnabled()) {
                        adminSounds.updateError(player);
                        messages.send(player, "pvp-override-active", "{prefix}{good}PvP is forced on by an admin event override.");
                        return true;
                    }
                    if (toggleService.enforceRegionRestriction(player)) {
                        adminSounds.updateError(player);
                        messages.send(player, "region-pvp-forced-enabled", "{prefix}{good}PvP was forced on for you in this region.");
                        return true;
                    }
                    toggleService.setPvpEnabled(player, true);
                    sounds.play(player, "pvp.enabled");
                    messages.send(player, "pvp-enabled", "{prefix}{good}PvP enabled.");
                    return true;
                }
                case "off", "disable", "disabled" -> {
                    if (toggleService.isPvpOverrideEnabled()) {
                        adminSounds.updateError(player);
                        messages.send(player, "pvp-override-denied", "{prefix}{bad}You cannot disable /pvp while admin event override is enabled.");
                        return true;
                    }
                    if (toggleService.enforceRegionRestriction(player)) {
                        adminSounds.updateError(player);
                        messages.send(player, "region-toggle-denied", "{prefix}{bad}You cannot disable /pvp inside this WorldGuard region.");
                        return true;
                    }
                    toggleService.setPvpEnabled(player, false);
                    sounds.play(player, "pvp.disabled");
                    sendDisabled(player);
                    return true;
                }
                case "status" -> {
                    sendStatus(player);
                    return true;
                }
                default -> {
                    messages.send(sender, "usage-toggle", "{prefix}{muted}Usage: {white}/{label} [on|off|status]", Map.of("{label}", label));
                    adminSounds.updateError(sender);
                    return true;
                }
            }
        }

        if (toggleService.isPvpOverrideEnabled()) {
            adminSounds.updateError(player);
            messages.send(player, "pvp-override-denied", "{prefix}{bad}You cannot disable /pvp while admin event override is enabled.");
            return true;
        }

        if (toggleService.enforceRegionRestriction(player)) {
            adminSounds.updateError(player);
            messages.send(player, "region-toggle-denied", "{prefix}{bad}You cannot disable /pvp inside this WorldGuard region.");
            return true;
        }

        boolean enabled = toggleService.toggle(player);
        if (enabled) {
            sounds.play(player, "pvp.enabled");
            messages.send(player, "pvp-enabled", "{prefix}{good}PvP enabled.");
        } else {
            sounds.play(player, "pvp.disabled");
            sendDisabled(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("fopvptoggle.pvptoggle") || args.length != 1) {
            return Collections.emptyList();
        }
        return partial(List.of("on", "off", "status"), args[0]);
    }

    private void sendStatus(Player player) {
        if (toggleService.isPvpOverrideEnabled()) {
            messages.send(player, "pvp-status-override", "{prefix}{muted}PvP status: {good}enabled {muted}(admin override active)");
            return;
        }
        boolean enabled = toggleService.isPvpEnabled(player);
        if (enabled) {
            messages.send(player, "pvp-status-enabled", "{prefix}{muted}PvP status: {good}enabled");
            return;
        }
        messages.send(player, "pvp-status-disabled", "{prefix}{muted}PvP status: {bad}disabled");
        if (toggleService.isPveProtected(player)) {
            messages.send(player, "pve-protection-active", "{prefix}{muted}PvE protection is also active.");
        }
    }

    private void sendDisabled(Player player) {
        messages.send(player, "pvp-disabled", "{prefix}{bad}PvP disabled.");
        if (toggleService.isPveProtected(player)) {
            messages.send(player, "pve-protection-active", "{prefix}{muted}PvE protection is also active.");
        }
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
