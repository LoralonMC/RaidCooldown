package dev.oakheart.raidcooldown.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import dev.oakheart.raidcooldown.config.ConfigManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Handles all player messaging with MiniMessage formatting support.
 * Empty messages are treated as disabled and not sent.
 */
public class MessageManager {

    private final ConfigManager configManager;
    private final MiniMessage miniMessage;

    // Message key constants
    public static final String ONLY_PLAYERS = "onlyPlayersMessage";
    public static final String PLAYER_NOT_FOUND = "playerNotFoundMessage";
    public static final String RELOAD_SUCCESS = "reloadMessage";
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

    public MessageManager(@NotNull ConfigManager configManager) {
        this.configManager = configManager;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void sendMessage(@NotNull CommandSender sender, @NotNull String messageKey, @NotNull TagResolver... resolvers) {
        String rawMessage = configManager.getMessage(messageKey);
        if (rawMessage.isEmpty()) return;
        sender.sendMessage(miniMessage.deserialize(rawMessage, resolvers));
    }

    public void sendCooldownMessage(@NotNull CommandSender sender, @NotNull String messageKey, @NotNull OfflinePlayer player, @NotNull Duration remainingTime) {
        String rawMessage = configManager.getMessage(messageKey);
        if (rawMessage.isEmpty()) return;
        sender.sendMessage(miniMessage.deserialize(rawMessage,
                Placeholder.unparsed("player", getPlayerName(player)),
                Placeholder.unparsed("time", formatDuration(remainingTime))));
    }

    public void sendRaidBlockedMessage(@NotNull CommandSender sender, @NotNull Duration remainingTime) {
        sendMessage(sender, RAID_BLOCKED,
                Placeholder.unparsed("time", formatDuration(remainingTime)));
    }

    @NotNull
    public String formatDuration(@NotNull Duration duration) {
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
        if (seconds > 0 || result.isEmpty()) {
            result.append(seconds).append(configManager.getMessage(SECOND_FORMAT, "s"));
        }

        return result.toString().trim();
    }

    @NotNull
    private String getPlayerName(@NotNull OfflinePlayer player) {
        String name = player.getName();
        return name != null ? name : "Unknown Player";
    }
}
