package dev.oakheart.raidcooldown.cooldown;

import dev.oakheart.raidcooldown.config.ConfigManager;
import dev.oakheart.raidcooldown.message.MessageManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages raid cooldowns for players with persistent storage and automatic cleanup.
 */
public class CooldownManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Logger logger;

    private final Map<UUID, Instant> cooldowns;
    private final Set<UUID> dirtyCooldowns;

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
     *
     * @param player The player attempting to start a raid
     * @return true if the raid was allowed and cooldown was set, false if on cooldown
     */
    public synchronized boolean canStartRaidAndSetCooldown(@NotNull Player player) {
        if (player.hasPermission("raidcooldown.bypass")) {
            return true;
        }

        UUID playerId = player.getUniqueId();
        Duration remaining = getRemainingCooldown(playerId);

        if (remaining.isZero() || remaining.isNegative()) {
            Instant cooldownEnd = calculateCooldownEnd();
            cooldowns.put(playerId, cooldownEnd);
            dirtyCooldowns.add(playerId);

            if (configManager.shouldLogCooldownActions()) {
                logger.info("Set raid cooldown for " + player.getName() + " until " + cooldownEnd);
            }

            return true;
        }

        messageManager.sendRaidBlockedMessage(player, remaining);
        return false;
    }

    @NotNull
    private Instant calculateCooldownEnd() {
        if (configManager.isSynchronizedResetEnabled()) {
            return calculateNextResetTime();
        }
        return Instant.now().plus(configManager.getCooldownDuration());
    }

    @NotNull
    private Instant calculateNextResetTime() {
        LocalTime resetTime = configManager.getResetTime();
        ZoneId serverZone = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(serverZone);

        ZonedDateTime todayReset = now.toLocalDate().atTime(resetTime).atZone(serverZone);

        if (!now.isBefore(todayReset)) {
            todayReset = todayReset.plusDays(1);
        }

        return todayReset.toInstant();
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
        dirtyCooldowns.add(playerId);

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
            messageManager.sendMessage(sender, messageKey,
                    Placeholder.unparsed("player", target.getName()));
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
        FileConfiguration cooldownConfig = configManager.getCooldownConfig();

        for (String key : new ArrayList<>(cooldownConfig.getKeys(false))) {
            try {
                UUID playerId = UUID.fromString(key);
                long epochSeconds = cooldownConfig.getLong(key, 0);
                Instant cooldownEnd = Instant.ofEpochSecond(epochSeconds);

                if (cooldownEnd.isAfter(now)) {
                    cooldowns.put(playerId, cooldownEnd);
                    loaded++;
                } else {
                    cooldownConfig.set(key, null);
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

    private void saveDirtyCooldowns() {
        if (dirtyCooldowns.isEmpty()) {
            return;
        }

        Set<UUID> toSave = new HashSet<>();
        dirtyCooldowns.removeIf(toSave::add);

        FileConfiguration cooldownConfig = configManager.getCooldownConfig();
        int saved = 0;
        for (UUID playerId : toSave) {
            Instant cooldownEnd = cooldowns.get(playerId);
            if (cooldownEnd != null) {
                cooldownConfig.set(playerId.toString(), cooldownEnd.getEpochSecond());
                saved++;
            } else {
                cooldownConfig.set(playerId.toString(), null);
            }
        }

        if (saved > 0 || toSave.size() > saved) {
            configManager.saveCooldownConfig();
            if (configManager.shouldLogCooldownActions() && saved > 0) {
                logger.fine("Batch saved " + saved + " cooldowns");
            }
        }
    }

    private void startCleanupTask() {
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredCooldowns();
            }
        };

        long intervalTicks = configManager.getCleanupIntervalTicks();
        if (intervalTicks > 0) {
            cleanupTask.runTaskTimer(plugin, intervalTicks, intervalTicks);
        }
    }

    private void startPeriodicSaveTask() {
        saveTask = new BukkitRunnable() {
            @Override
            public void run() {
                saveDirtyCooldowns();
            }
        };

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
                dirtyCooldowns.add(entry.getKey());
                cleaned++;
            }
        }

        if (cleaned > 0) {
            logger.info("Cleaned up " + cleaned + " expired cooldowns");
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

        saveDirtyCooldowns();
        logger.info("CooldownManager shut down successfully");
    }

    /**
     * Restarts scheduled tasks with current configuration values.
     * Called after a successful config reload.
     */
    public void restartTasks() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        if (saveTask != null) {
            saveTask.cancel();
        }
        startCleanupTask();
        startPeriodicSaveTask();
        logger.info("Scheduled tasks restarted with updated configuration");
    }

    public int getActiveCooldownCount() {
        Instant now = Instant.now();
        int count = 0;
        for (Instant end : cooldowns.values()) {
            if (end.isAfter(now)) {
                count++;
            }
        }
        return count;
    }
}
