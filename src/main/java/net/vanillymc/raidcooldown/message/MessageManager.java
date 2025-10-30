package net.vanillymc.raidcooldown.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.vanillymc.raidcooldown.config.ConfigManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles all player messaging with MiniMessage formatting support.
 * <p>
 * Provides rich text formatting using the Adventure API and MiniMessage syntax.
 * Features include:
 * <ul>
 *   <li>Color and formatting support via MiniMessage</li>
 *   <li>Placeholder replacement for dynamic content</li>
 *   <li>Human-readable duration formatting</li>
 *   <li>Centralized message management</li>
 * </ul>
 * </p>
 *
 * @author Loralon
 * @version 1.1.0
 */
public class MessageManager {

    private final ConfigManager configManager;
    private final MiniMessage miniMessage;

    // Message key constants
    public static final String ONLY_PLAYERS = "onlyPlayersMessage";
    public static final String NO_PERMISSION = "noPermissionMessage";
    public static final String PLAYER_NOT_FOUND = "playerNotFoundMessage";
    public static final String RELOAD_SUCCESS = "reloadMessage";
    public static final String USAGE = "usage";
    public static final String RAID_BLOCKED = "raidCooldownMessage";
    public static final String COOLDOWN_REMAINING_SELF = "cooldownRemainingMessage";
    public static final String COOLDOWN_REMAINING_OTHER = "cooldownRemainingOtherMessage";
    public static final String RAID_AVAILABLE_SELF = "raidAvailableMessage";
    public static final String RAID_AVAILABLE_OTHER = "raidAvailableOtherMessage";
    public static final String COOLDOWN_RESET = "resetCooldownMessage";
    public static final String COOLDOWN_RESET_NOTIFICATION = "cooldownResetNotification";
    public static final String RELOAD_ERROR = "reloadError";
    public static final String INFO_HEADER = "infoHeader";
    public static final String INFO_ACTIVE_COOLDOWNS = "infoActiveCooldowns";
    public static final String INFO_COOLDOWN_DURATION = "infoCooldownDuration";
    public static final String INFO_CONFIG_VALID = "infoConfigValid";

    // Time format keys
    private static final String HOUR_FORMAT = "hour";
    private static final String MINUTE_FORMAT = "minute";
    private static final String SECOND_FORMAT = "second";

    public MessageManager(ConfigManager configManager) {
        this.configManager = configManager;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void sendMessage(CommandSender sender, String messageKey) {
        sendMessage(sender, messageKey, new HashMap<>());
    }

    public void sendMessage(CommandSender sender, String messageKey, Map<String, String> placeholders) {
        Component message = buildMessage(messageKey, placeholders);
        sender.sendMessage(message);
    }

    public void sendMessage(CommandSender sender, String messageKey, String placeholder, String value) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put(placeholder, value);
        sendMessage(sender, messageKey, placeholders);
    }

    public Component buildMessage(String messageKey, Map<String, String> placeholders) {
        String rawMessage = configManager.getMessage(messageKey);

        // Replace placeholders
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rawMessage = rawMessage.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return miniMessage.deserialize(rawMessage);
    }

    public Component buildCooldownMessage(String messageKey, OfflinePlayer player, Duration remainingTime) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", getPlayerName(player));
        placeholders.put("time", formatDuration(remainingTime));

        return buildMessage(messageKey, placeholders);
    }

    public void sendCooldownMessage(CommandSender sender, String messageKey, OfflinePlayer player, Duration remainingTime) {
        Component message = buildCooldownMessage(messageKey, player, remainingTime);
        sender.sendMessage(message);
    }

    public void sendRaidBlockedMessage(CommandSender sender, Duration remainingTime) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("time", formatDuration(remainingTime));
        sendMessage(sender, RAID_BLOCKED, placeholders);
    }

    private String formatDuration(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return "0" + configManager.getMessage(SECOND_FORMAT, "s");
        }

        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder result = new StringBuilder();

        if (hours > 0) {
            result.append(hours).append(configManager.getMessage(HOUR_FORMAT, "h "));
        }
        if (minutes > 0) {
            result.append(minutes).append(configManager.getMessage(MINUTE_FORMAT, "m "));
        }
        if (seconds > 0 || result.length() == 0) {
            result.append(seconds).append(configManager.getMessage(SECOND_FORMAT, "s"));
        }

        return result.toString().trim();
    }

    private String getPlayerName(OfflinePlayer player) {
        String name = player.getName();
        return name != null ? name : "Unknown Player";
    }

    // Utility method for common message patterns
    public Component formatPlayerMessage(String messageKey, OfflinePlayer player) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", getPlayerName(player));
        return buildMessage(messageKey, placeholders);
    }
}