package dev.oakheart.raidcooldown.placeholder;

import dev.oakheart.raidcooldown.RaidCooldown;
import dev.oakheart.raidcooldown.config.ConfigManager;
import dev.oakheart.raidcooldown.cooldown.CooldownManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * PlaceholderAPI expansion for RaidCooldown.
 * <p>
 * Provides the following placeholders:
 * <ul>
 *   <li>%raidcooldown_time% - Formatted remaining time or ready message</li>
 *   <li>%raidcooldown_ready% - "true" if player can raid, "false" if on cooldown</li>
 *   <li>%raidcooldown_seconds% - Raw seconds remaining (0 if ready)</li>
 * </ul>
 * </p>
 *
 * @author Loralon
 * @version 1.3.0
 */
@SuppressWarnings("deprecation") // PlaceholderExpansion API uses deprecated methods
public class RaidCooldownExpansion extends PlaceholderExpansion {

    private final RaidCooldown plugin;
    private final CooldownManager cooldownManager;
    private final ConfigManager configManager;

    public RaidCooldownExpansion(@NotNull RaidCooldown plugin) {
        this.plugin = plugin;
        this.cooldownManager = plugin.getCooldownManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "raidcooldown";
    }

    @Override
    @NotNull
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    @NotNull
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        // This expansion should persist through reloads
        return true;
    }

    @Override
    @Nullable
    public String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return null;
        }

        Duration remaining = cooldownManager.getRemainingCooldown(player.getUniqueId());

        return switch (params.toLowerCase()) {
            case "time" -> formatTime(remaining);
            case "ready" -> String.valueOf(remaining.isZero());
            case "seconds" -> String.valueOf(remaining.getSeconds());
            default -> null;
        };
    }

    @NotNull
    private String formatTime(@NotNull Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return configManager.getPlaceholderReadyMessage();
        }

        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder result = new StringBuilder();

        if (hours > 0) {
            result.append(hours).append("h ");
        }
        if (minutes > 0) {
            result.append(minutes).append("m ");
        }
        if (seconds > 0 || result.isEmpty()) {
            result.append(seconds).append("s");
        }

        return result.toString().trim();
    }
}
