package net.vanillymc.raidcooldown.cooldown;

import net.vanillymc.raidcooldown.config.ConfigManager;
import net.vanillymc.raidcooldown.message.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class CooldownManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Logger logger;

    // Use ConcurrentHashMap for thread safety
    private final Map<UUID, Instant> cooldowns;

    // Cleanup task
    private BukkitRunnable cleanupTask;

    public CooldownManager(JavaPlugin plugin, ConfigManager configManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.logger = plugin.getLogger();
        this.cooldowns = new ConcurrentHashMap<>();

        loadCooldownData();
        startCleanupTask();
    }

    public boolean canStartRaid(Player player) {
        // Check bypass permission first
        if (player.hasPermission("raidcooldown.bypass")) {
            return true;
        }

        UUID playerId = player.getUniqueId();
        Duration remaining = getRemainingCooldown(playerId);

        if (remaining.isZero() || remaining.isNegative()) {
            return true;
        }

        // Send cooldown message to player
        messageManager.sendRaidBlockedMessage(player, remaining);
        return false;
    }

    public void setCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        Instant cooldownEnd = Instant.now().plus(configManager.getCooldownDuration());

        cooldowns.put(playerId, cooldownEnd);
        saveCooldownData(playerId, cooldownEnd);

        logger.info("Set raid cooldown for " + player.getName() + " until " + cooldownEnd);
    }

    public Duration getRemainingCooldown(UUID playerId) {
        Instant cooldownEnd = cooldowns.get(playerId);

        if (cooldownEnd == null) {
            return Duration.ZERO;
        }

        Duration remaining = Duration.between(Instant.now(), cooldownEnd);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public boolean hasCooldown(UUID playerId) {
        return !getRemainingCooldown(playerId).isZero();
    }

    public void removeCooldown(UUID playerId) {
        cooldowns.remove(playerId);

        // Remove from persistent storage
        configManager.getCooldownConfig().set(playerId.toString(), null);
        configManager.saveCooldownConfig();

        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        logger.info("Removed raid cooldown for " + (player.getName() != null ? player.getName() : playerId));
    }

    public void sendCooldownStatus(Player sender, Player target) {
        UUID targetId = target.getUniqueId();
        Duration remaining = getRemainingCooldown(targetId);
        boolean isSelfCheck = sender.equals(target);

        if (remaining.isZero()) {
            String messageKey = isSelfCheck ?
                    MessageManager.RAID_AVAILABLE_SELF :
                    MessageManager.RAID_AVAILABLE_OTHER;
            messageManager.sendMessage(sender, messageKey, "player", target.getName());
        } else {
            String messageKey = isSelfCheck ?
                    MessageManager.COOLDOWN_REMAINING_SELF :
                    MessageManager.COOLDOWN_REMAINING_OTHER;
            messageManager.sendCooldownMessage(sender, messageKey, target, remaining);
        }
    }

    private void loadCooldownData() {
        int loaded = 0;
        int expired = 0;
        Instant now = Instant.now();

        for (String key : configManager.getCooldownConfig().getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                long epochSeconds = configManager.getCooldownConfig().getLong(key);
                Instant cooldownEnd = Instant.ofEpochSecond(epochSeconds);

                if (cooldownEnd.isAfter(now)) {
                    cooldowns.put(playerId, cooldownEnd);
                    loaded++;
                } else {
                    // Remove expired cooldown from config
                    configManager.getCooldownConfig().set(key, null);
                    expired++;
                }
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid UUID in cooldown data: " + key);
            }
        }

        if (expired > 0) {
            configManager.saveCooldownConfig();
        }

        logger.info("Loaded " + loaded + " active cooldowns, cleaned up " + expired + " expired cooldowns");
    }

    private void saveCooldownData(UUID playerId, Instant cooldownEnd) {
        configManager.getCooldownConfig().set(playerId.toString(), cooldownEnd.getEpochSecond());
        configManager.saveCooldownConfig();
    }

    private void startCleanupTask() {
        // Clean up expired cooldowns every 10 minutes
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredCooldowns();
            }
        };

        // Run every 10 minutes (12000 ticks)
        cleanupTask.runTaskTimerAsynchronously(plugin, 12000L, 12000L);
    }

    private void cleanupExpiredCooldowns() {
        Instant now = Instant.now();
        int cleaned = 0;

        Iterator<Map.Entry<UUID, Instant>> iterator = cooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Instant> entry = iterator.next();
            if (entry.getValue().isBefore(now)) {
                iterator.remove();

                // Also remove from persistent storage
                configManager.getCooldownConfig().set(entry.getKey().toString(), null);
                cleaned++;
            }
        }

        if (cleaned > 0) {
            configManager.saveCooldownConfig();
            logger.info("Cleaned up " + cleaned + " expired cooldowns");
        }
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }

        // Save all current cooldowns
        saveAllCooldowns();
        logger.info("CooldownManager shut down successfully");
    }

    private void saveAllCooldowns() {
        for (Map.Entry<UUID, Instant> entry : cooldowns.entrySet()) {
            saveCooldownData(entry.getKey(), entry.getValue());
        }
    }

    // Utility methods
    public int getActiveCooldownCount() {
        return cooldowns.size();
    }

    public Map<UUID, Duration> getAllActiveCooldowns() {
        Map<UUID, Duration> result = new HashMap<>();
        Instant now = Instant.now();

        for (Map.Entry<UUID, Instant> entry : cooldowns.entrySet()) {
            Duration remaining = Duration.between(now, entry.getValue());
            if (!remaining.isNegative()) {
                result.put(entry.getKey(), remaining);
            }
        }

        return result;
    }
}