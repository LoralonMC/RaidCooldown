package dev.oakheart.raidcooldown.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import dev.oakheart.config.ConfigManager;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MessageManager {

    private static final String[] MESSAGE_KEYS = {
            "raid-blocked", "cooldown-remaining", "cooldown-remaining-other",
            "raid-available", "raid-available-other",
            "cooldown-reset", "cooldown-reset-notification",
            "reload-success", "reload-error",
            "only-players", "player-not-found",
            "info-header", "info-active-cooldowns", "info-cooldown-duration", "info-config-valid"
    };

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, String> texts = new HashMap<>();
    private final Map<String, String> displays = new HashMap<>();

    private String hourSuffix;
    private String minuteSuffix;
    private String secondSuffix;

    public void load(ConfigManager config) {
        texts.clear();
        displays.clear();

        for (String key : MESSAGE_KEYS) {
            texts.put(key, config.getString("messages." + key + ".text", ""));
            displays.put(key, config.getString("messages." + key + ".display", "chat"));
        }

        hourSuffix = config.getString("messages.time-format.hour", "h ");
        minuteSuffix = config.getString("messages.time-format.minute", "m ");
        secondSuffix = config.getString("messages.time-format.second", "s");
    }

    // --- Send helpers ---

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        parse(key, resolvers).ifPresent(component -> {
            String display = displays.getOrDefault(key, "chat");
            if ("action_bar".equals(display) && sender instanceof Player player) {
                player.sendActionBar(component);
            } else {
                sender.sendMessage(component);
            }
        });
    }

    // --- Named convenience methods ---

    public void sendRaidBlocked(CommandSender sender, Duration remaining) {
        send(sender, "raid-blocked", Placeholder.unparsed("time", formatDuration(remaining)));
    }

    public void sendCooldownRemaining(CommandSender sender, Duration remaining) {
        send(sender, "cooldown-remaining", Placeholder.unparsed("time", formatDuration(remaining)));
    }

    public void sendCooldownRemainingOther(CommandSender sender, String playerName, Duration remaining) {
        send(sender, "cooldown-remaining-other",
                Placeholder.unparsed("player", playerName),
                Placeholder.unparsed("time", formatDuration(remaining)));
    }

    public void sendRaidAvailable(CommandSender sender) {
        send(sender, "raid-available");
    }

    public void sendRaidAvailableOther(CommandSender sender, String playerName) {
        send(sender, "raid-available-other", Placeholder.unparsed("player", playerName));
    }

    public void sendCooldownReset(CommandSender sender, String playerName) {
        send(sender, "cooldown-reset", Placeholder.unparsed("player", playerName));
    }

    public void sendCooldownResetNotification(CommandSender sender) {
        send(sender, "cooldown-reset-notification");
    }

    public void sendReloadSuccess(CommandSender sender) {
        send(sender, "reload-success");
    }

    public void sendReloadError(CommandSender sender, String error) {
        send(sender, "reload-error", Placeholder.unparsed("error", error));
    }

    public void sendOnlyPlayers(CommandSender sender) {
        send(sender, "only-players");
    }

    public void sendPlayerNotFound(CommandSender sender, String playerName) {
        send(sender, "player-not-found", Placeholder.unparsed("player", playerName));
    }

    public void sendInfoHeader(CommandSender sender) {
        send(sender, "info-header");
    }

    public void sendInfoActiveCooldowns(CommandSender sender, int count) {
        send(sender, "info-active-cooldowns", Placeholder.unparsed("count", String.valueOf(count)));
    }

    public void sendInfoCooldownDuration(CommandSender sender, long hours) {
        send(sender, "info-cooldown-duration", Placeholder.unparsed("duration", String.valueOf(hours)));
    }

    public void sendInfoConfigValid(CommandSender sender, boolean valid) {
        send(sender, "info-config-valid", Placeholder.unparsed("valid", String.valueOf(valid)));
    }

    // --- Duration formatting ---

    public String formatDuration(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return "0" + secondSuffix;
        }

        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder result = new StringBuilder();

        if (hours > 0) {
            result.append(hours).append(hourSuffix);
        }
        if (minutes > 0) {
            result.append(minutes).append(minuteSuffix);
        }
        if (seconds > 0 || result.isEmpty()) {
            result.append(seconds).append(secondSuffix);
        }

        return result.toString().trim();
    }

    // --- Internal ---

    private Optional<Component> parse(String key, TagResolver... resolvers) {
        String text = texts.get(key);
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(miniMessage.deserialize(text, resolvers));
    }
}
