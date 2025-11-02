package dev.oakheart.raidcooldown.cooldown;

import dev.oakheart.raidcooldown.config.ConfigManager;
import dev.oakheart.raidcooldown.message.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages raid cooldowns for players with persistent storage and automatic cleanup.
 * <p>
 * This class handles all cooldown logic including:
 * <ul>
 *   <li>Checking if players can start raids</li>
 *   <li>Setting cooldowns atomically to prevent race conditions</li>
 *   <li>Persistent storage with batch saving for performance</li>
 *   <li>Automatic cleanup of expired cooldowns</li>
 *   <li>Thread-safe operations using ConcurrentHashMap</li>
 * </ul>
 * </p>
 *
 * @author Loralon
 * @version 1.2.0
 */
public class CooldownManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Logger logger;

    // Use ConcurrentHashMap for thread safety
    private final Map<UUID, Instant> cooldowns;

    // Track dirty cooldowns that need to be saved (for batch saving)
    private final Set<UUID> dirtyCooldowns;

    // Cleanup task
    private BukkitRunnable cleanupTask;
    private BukkitRunnable saveTask;

    public CooldownManager(@NotNull JavaPlugin plugin, @NotNull ConfigManager configManager, @NotNull MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.logger = plugin.getLogger();
        this.cooldowns = new ConcurrentHashMap<>();
        this.dirtyCooldowns = ConcurrentHashMap.newKeySet();

        loadCooldownData();
        startCleanupTask();
        startPeriodicSaveTask();
    }

    /**
     * Atomically checks if a player can start a raid and sets the cooldown if they can.
     * This method is synchronized to prevent race conditions where multiple threads
     * might try to start a raid for the same player simultaneously.
     *
     * @param player The player attempting to start a raid
     * @return true if the raid was allowed and cooldown was set, false if on cooldown
     */
    public synchronized boolean canStartRaidAndSetCooldown(@NotNull Player player) {
        // Check bypass permission first
        if (player.hasPermission("raidcooldown.bypass")) {
            return true;
        }

        UUID playerId = player.getUniqueId();
        Duration remaining = getRemainingCooldown(playerId);

        if (remaining.isZero() || remaining.isNegative()) {
            // Player can start raid, set cooldown immediately (atomic operation)
            Instant cooldownEnd = Instant.now().plus(configManager.getCooldownDuration());
            cooldowns.put(playerId, cooldownEnd);

            // Mark as dirty for batch saving
            dirtyCooldowns.add(playerId);

            if (configManager.shouldLogCooldownActions()) {
                logger.info("Set raid cooldown for " + player.getName() + " until " + cooldownEnd);
            }

            return true;
        }

        // Send cooldown message to player
        messageManager.sendRaidBlockedMessage(player, remaining);
        return false;
    }

    @NotNull
    public Duration getRemainingCooldown(@NotNull UUID playerId) {
        Instant cooldownEnd = cooldowns.get(playerId);

        if (cooldownEnd == null) {
            return Duration.ZERO;
        }

        Duration remaining = Duration.between(Instant.now(), cooldownEnd);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public boolean hasCooldown(@NotNull UUID playerId) {
        return !getRemainingCooldown(playerId).isZero();
    }

    public void removeCooldown(@NotNull UUID playerId) {
        cooldowns.remove(playerId);
        dirtyCooldowns.add(playerId); // Mark as dirty to ensure removal is saved

        if (configManager.shouldLogCooldownActions()) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
            logger.info("Removed raid cooldown for " + (player.getName() != null ? player.getName() : playerId));
        }
    }

    public void sendCooldownStatus(@NotNull org.bukkit.command.CommandSender sender, @NotNull Player target) {
        UUID targetId = target.getUniqueId();
        Duration remaining = getRemainingCooldown(targetId);
        boolean isSelfCheck = sender instanceof Player && sender.equals(target);

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

    /**
     * Saves all dirty cooldowns to persistent storage in a batch operation.
     * This is much more efficient than saving each cooldown individually.
     */
    private void saveDirtyCooldowns() {
        if (dirtyCooldowns.isEmpty()) {
            return;
        }

        // Create a copy to avoid concurrent modification
        Set<UUID> toSave = Set.copyOf(dirtyCooldowns);
        dirtyCooldowns.clear();

        int saved = 0;
        for (UUID playerId : toSave) {
            Instant cooldownEnd = cooldowns.get(playerId);
            if (cooldownEnd != null) {
                configManager.getCooldownConfig().set(playerId.toString(), cooldownEnd.getEpochSecond());
                saved++;
            } else {
                // Cooldown was removed, delete from config
                configManager.getCooldownConfig().set(playerId.toString(), null);
            }
        }

        // Save once for all changes
        if (saved > 0 || toSave.size() > saved) {
            configManager.saveCooldownConfig();
            if (configManager.shouldLogCooldownActions() && saved > 0) {
                logger.fine("Batch saved " + saved + " cooldowns");
            }
        }
    }

    private void startCleanupTask() {
        // Clean up expired cooldowns periodically
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredCooldowns();
            }
        };

        // Get cleanup interval from config (default 10 minutes = 12000 ticks)
        long intervalTicks = configManager.getCleanupIntervalTicks();
        if (intervalTicks > 0) {
            // Run synchronously to ensure thread-safe config saves
            cleanupTask.runTaskTimer(plugin, intervalTicks, intervalTicks);
        }
    }

    private void startPeriodicSaveTask() {
        // Save dirty cooldowns every 30 seconds to reduce disk I/O
        saveTask = new BukkitRunnable() {
            @Override
            public void run() {
                saveDirtyCooldowns();
            }
        };

        // Run every 30 seconds (600 ticks)
        saveTask.runTaskTimer(plugin, 600L, 600L);
    }

    private void cleanupExpiredCooldowns() {
        Instant now = Instant.now();
        int cleaned = 0;

        Iterator<Map.Entry<UUID, Instant>> iterator = cooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Instant> entry = iterator.next();
            if (entry.getValue().isBefore(now)) {
                iterator.remove();
                dirtyCooldowns.add(entry.getKey()); // Mark as dirty for batch deletion
                cleaned++;
            }
        }

        if (cleaned > 0) {
            logger.info("Cleaned up " + cleaned + " expired cooldowns");
            // Save immediately after cleanup
            saveDirtyCooldowns();
        }
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        if (saveTask != null) {
            saveTask.cancel();
        }

        // Save any remaining dirty cooldowns before shutdown
        saveDirtyCooldowns();
        logger.info("CooldownManager shut down successfully");
    }

    // Utility methods
    public int getActiveCooldownCount() {
        return cooldowns.size();
    }

    @NotNull
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
