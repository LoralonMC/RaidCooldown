package dev.oakheart.raidcooldown.cooldown;

import dev.oakheart.raidcooldown.config.ConfigManager;
import dev.oakheart.raidcooldown.message.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
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

public class CooldownManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Logger logger;

    private final File cooldownFile;
    private FileConfiguration cooldownConfig;

    private final Map<UUID, Instant> cooldowns;
    private final Set<UUID> dirtyCooldowns;

    private BukkitTask cleanupTask;
    private BukkitTask saveTask;

    public CooldownManager(JavaPlugin plugin, ConfigManager configManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.logger = plugin.getLogger();
        this.cooldownFile = new File(plugin.getDataFolder(), "cooldowns.yml");
        this.cooldowns = new ConcurrentHashMap<>();
        this.dirtyCooldowns = ConcurrentHashMap.newKeySet();

        setupCooldownFile();
        loadCooldownData();
        startCleanupTask();
        startPeriodicSaveTask();
    }

    public synchronized boolean canStartRaidAndSetCooldown(Player player) {
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

        messageManager.sendRaidBlocked(player, remaining);
        return false;
    }

    private Instant calculateCooldownEnd() {
        if (configManager.isSynchronizedResetEnabled()) {
            return calculateNextResetTime();
        }
        return Instant.now().plus(configManager.getCooldownDuration());
    }

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
        dirtyCooldowns.add(playerId);

        if (configManager.shouldLogCooldownActions()) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
            logger.info("Removed raid cooldown for " + (player.getName() != null ? player.getName() : playerId));
        }
    }

    public void sendCooldownStatus(CommandSender sender, Player target) {
        UUID targetId = target.getUniqueId();
        Duration remaining = getRemainingCooldown(targetId);
        boolean isSelfCheck = sender instanceof Player && sender.equals(target);

        if (remaining.isZero()) {
            if (isSelfCheck) {
                messageManager.sendRaidAvailable(sender);
            } else {
                messageManager.sendRaidAvailableOther(sender, target.getName());
            }
        } else {
            if (isSelfCheck) {
                messageManager.sendCooldownRemaining(sender, remaining);
            } else {
                messageManager.sendCooldownRemainingOther(sender, target.getName(), remaining);
            }
        }
    }

    private void setupCooldownFile() {
        if (!cooldownFile.exists()) {
            try {
                cooldownFile.getParentFile().mkdirs();
                cooldownFile.createNewFile();
                logger.info("Created new cooldowns.yml file");
            } catch (IOException e) {
                throw new RuntimeException("Could not create cooldowns.yml", e);
            }
        }
        this.cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile);
    }

    private void loadCooldownData() {
        int loaded = 0;
        int expired = 0;
        Instant now = Instant.now();

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
            saveCooldownFile();
        }

        logger.info("Loaded " + loaded + " active cooldowns, cleaned up " + expired + " expired cooldowns");
    }

    private void saveDirtyCooldowns() {
        if (dirtyCooldowns.isEmpty()) {
            return;
        }

        Set<UUID> toSave = new HashSet<>();
        dirtyCooldowns.removeIf(toSave::add);

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
            saveCooldownFile();
            if (configManager.shouldLogCooldownActions() && saved > 0) {
                logger.info("Batch saved " + saved + " cooldowns");
            }
        }
    }

    private void saveCooldownFile() {
        try {
            cooldownConfig.save(cooldownFile);
        } catch (IOException e) {
            logger.severe("Could not save cooldown data: " + e.getMessage());
        }
    }

    private void startCleanupTask() {
        long intervalTicks = configManager.getCleanupIntervalTicks();
        if (intervalTicks > 0) {
            cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredCooldowns, intervalTicks, intervalTicks);
        }
    }

    private void startPeriodicSaveTask() {
        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveDirtyCooldowns, 600L, 600L);
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
