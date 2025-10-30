package net.vanillymc.raidcooldown.command;

import net.vanillymc.raidcooldown.config.ConfigManager;
import net.vanillymc.raidcooldown.cooldown.CooldownManager;
import net.vanillymc.raidcooldown.message.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command handler for the /raidcooldown command and its aliases.
 * <p>
 * Provides comprehensive raid cooldown management with subcommands:
 * <ul>
 *   <li>/raidcooldown - Check own cooldown status</li>
 *   <li>/rc check &lt;player&gt; - Check another player's cooldown</li>
 *   <li>/rc reset &lt;player&gt; - Reset a player's cooldown</li>
 *   <li>/rc reload - Reload plugin configuration</li>
 *   <li>/rc info - View plugin information and statistics</li>
 * </ul>
 * Includes intelligent tab completion based on permissions.
 * </p>
 *
 * @author Loralon
 * @version 1.0.0
 */
public class RaidCooldownCommand implements CommandExecutor, TabCompleter {

    private final CooldownManager cooldownManager;
    private final MessageManager messageManager;
    private final ConfigManager configManager;

    // Permission constants
    private static final String PERM_CHECK = "raidcooldown.check";
    private static final String PERM_RESET = "raidcooldown.reset";
    private static final String PERM_RELOAD = "raidcooldown.reload";
    private static final String PERM_INFO = "raidcooldown.info";

    // Subcommands
    private static final String CMD_CHECK = "check";
    private static final String CMD_RESET = "reset";
    private static final String CMD_RELOAD = "reload";
    private static final String CMD_INFO = "info";

    public RaidCooldownCommand(CooldownManager cooldownManager, MessageManager messageManager, ConfigManager configManager) {
        this.cooldownManager = cooldownManager;
        this.messageManager = messageManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // No arguments - check own cooldown (players only)
        if (args.length == 0) {
            return handleSelfCheck(sender);
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case CMD_CHECK:
                return handleCheck(sender, args);
            case CMD_RESET:
                return handleReset(sender, args);
            case CMD_RELOAD:
                return handleReload(sender);
            case CMD_INFO:
                return handleInfo(sender);
            default:
                messageManager.sendMessage(sender, MessageManager.USAGE);
                return true;
        }
    }

    private boolean handleSelfCheck(CommandSender sender) {
        if (!(sender instanceof Player)) {
            messageManager.sendMessage(sender, MessageManager.ONLY_PLAYERS);
            return true;
        }

        Player player = (Player) sender;
        cooldownManager.sendCooldownStatus(player, player);
        return true;
    }

    private boolean handleCheck(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_CHECK)) {
            messageManager.sendMessage(sender, MessageManager.NO_PERMISSION);
            return true;
        }

        if (args.length < 2) {
            messageManager.sendMessage(sender, MessageManager.USAGE);
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messageManager.sendMessage(sender, MessageManager.PLAYER_NOT_FOUND, "player", args[1]);
            return true;
        }

        cooldownManager.sendCooldownStatus(sender, target);

        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_RESET)) {
            messageManager.sendMessage(sender, MessageManager.NO_PERMISSION);
            return true;
        }

        if (args.length < 2) {
            messageManager.sendMessage(sender, MessageManager.USAGE);
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messageManager.sendMessage(sender, MessageManager.PLAYER_NOT_FOUND, "player", args[1]);
            return true;
        }

        cooldownManager.removeCooldown(target.getUniqueId());

        messageManager.sendMessage(sender, MessageManager.COOLDOWN_RESET, "player", target.getName());
        messageManager.sendMessage(target, MessageManager.COOLDOWN_RESET_NOTIFICATION);

        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERM_RELOAD)) {
            messageManager.sendMessage(sender, MessageManager.NO_PERMISSION);
            return true;
        }

        try {
            configManager.reload();
            messageManager.sendMessage(sender, MessageManager.RELOAD_SUCCESS);
        } catch (Exception e) {
            messageManager.sendMessage(sender, MessageManager.RELOAD_ERROR, "error", e.getMessage());
        }

        return true;
    }

    private boolean handleInfo(CommandSender sender) {
        if (!sender.hasPermission(PERM_INFO)) {
            messageManager.sendMessage(sender, MessageManager.NO_PERMISSION);
            return true;
        }

        // Show plugin information
        int activeCooldowns = cooldownManager.getActiveCooldownCount();
        long cooldownHours = configManager.getCooldownDuration().toHours();
        boolean configValid = configManager.isValidConfig();

        messageManager.sendMessage(sender, MessageManager.INFO_HEADER);
        messageManager.sendMessage(sender, MessageManager.INFO_ACTIVE_COOLDOWNS, "count", String.valueOf(activeCooldowns));
        messageManager.sendMessage(sender, MessageManager.INFO_COOLDOWN_DURATION, "duration", String.valueOf(cooldownHours));
        messageManager.sendMessage(sender, MessageManager.INFO_CONFIG_VALID, "valid", String.valueOf(configValid));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument - subcommands
            List<String> subCommands = new ArrayList<>();

            if (sender.hasPermission(PERM_CHECK)) {
                subCommands.add(CMD_CHECK);
            }
            if (sender.hasPermission(PERM_RESET)) {
                subCommands.add(CMD_RESET);
            }
            if (sender.hasPermission(PERM_RELOAD)) {
                subCommands.add(CMD_RELOAD);
            }
            if (sender.hasPermission(PERM_INFO)) {
                subCommands.add(CMD_INFO);
            }

            String input = args[0].toLowerCase();
            completions.addAll(subCommands.stream()
                    .filter(cmd -> cmd.startsWith(input))
                    .collect(Collectors.toList()));

        } else if (args.length == 2) {
            // Second argument - player names for check/reset
            String subCommand = args[0].toLowerCase();
            if ((CMD_CHECK.equals(subCommand) && sender.hasPermission(PERM_CHECK)) ||
                    (CMD_RESET.equals(subCommand) && sender.hasPermission(PERM_RESET))) {

                String input = args[1].toLowerCase();
                completions.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList()));
            }
        }

        return completions;
    }
}