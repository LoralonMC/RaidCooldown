package dev.oakheart.raidcooldown.config;

import dev.oakheart.raidcooldown.RaidCooldown;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ConfigManager {

    private static final int DEFAULT_COOLDOWN_SECONDS = 86400;
    private static final int DEFAULT_CLEANUP_INTERVAL = 10;
    private static final LocalTime DEFAULT_RESET_TIME = LocalTime.MIDNIGHT;
    private static final String DEFAULT_READY_MESSAGE = "Ready";

    private final RaidCooldown plugin;
    private final Logger logger;
    private final File configFile;
    private FileConfiguration config;

    // Cached config values
    private Duration cachedCooldownDuration;
    private boolean cachedAutoCleanup;
    private int cachedCleanupIntervalMinutes;
    private boolean cachedLogActions;
    private boolean cachedSynchronizedResetEnabled;
    private LocalTime cachedResetTime;
    private String cachedPlaceholderReadyMessage;

    public ConfigManager(RaidCooldown plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
    }

    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        mergeDefaults();

        if (!validate(config)) {
            throw new RuntimeException("Configuration validation failed");
        }

        cacheValues();
    }

    public boolean reload() {
        FileConfiguration newConfig = YamlConfiguration.loadConfiguration(configFile);

        if (!validate(newConfig)) {
            logger.warning("Configuration reload failed validation. Keeping previous configuration.");
            return false;
        }

        this.config = newConfig;

        // Set defaults for fallback values
        try (var stream = plugin.getResource("config.yml")) {
            if (stream != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            logger.warning("Could not load config defaults: " + e.getMessage());
        }

        cacheValues();
        logger.info("Configuration reloaded successfully.");
        return true;
    }

    private void mergeDefaults() {
        try (var stream = plugin.getResource("config.yml")) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                config.setDefaults(defaults);

                if (hasNewKeys(defaults)) {
                    config.options().copyDefaults(true);
                    config.save(configFile);
                    logger.info("Config updated with new default values.");
                }
            }
        } catch (IOException e) {
            logger.warning("Could not save config defaults: " + e.getMessage());
        }
    }

    private boolean hasNewKeys(FileConfiguration defaults) {
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key) && !config.contains(key, true)) {
                return true;
            }
        }
        return false;
    }

    private boolean validate(FileConfiguration configToValidate) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Validate cooldown duration
        int cooldownSeconds = configToValidate.getInt("raid-cooldown-seconds", DEFAULT_COOLDOWN_SECONDS);
        if (cooldownSeconds < 0) {
            errors.add("Invalid 'raid-cooldown-seconds': " + cooldownSeconds + " (must be non-negative)");
        } else if (cooldownSeconds > 604800) {
            warnings.add("'raid-cooldown-seconds' is very high (" + cooldownSeconds + "s = " + (cooldownSeconds / 86400) + " days)");
        }

        // Validate cleanup interval
        int cleanupInterval = configToValidate.getInt("settings.cleanup-interval-minutes", DEFAULT_CLEANUP_INTERVAL);
        if (cleanupInterval < 0) {
            warnings.add("Invalid 'cleanup-interval-minutes' (negative value) - using default: " + DEFAULT_CLEANUP_INTERVAL);
        } else if (cleanupInterval > 0 && cleanupInterval < 5) {
            warnings.add("'cleanup-interval-minutes' is very low (" + cleanupInterval + " minutes) - may impact performance");
        }

        // Validate synchronized reset settings
        if (configToValidate.getBoolean("synchronized-reset.enabled", false)) {
            String resetTimeStr = configToValidate.getString("synchronized-reset.reset-time", "00:00");
            try {
                LocalTime parsed = LocalTime.parse(resetTimeStr, DateTimeFormatter.ofPattern("H:mm"));
                logger.info("Synchronized reset enabled - cooldowns will reset daily at " + parsed);
            } catch (DateTimeParseException e) {
                warnings.add("Invalid reset time format '" + resetTimeStr + "' - using default (00:00)");
            }
        }

        if (!errors.isEmpty()) {
            logger.severe("=== Configuration Errors ===");
            for (String error : errors) {
                logger.severe("  - " + error);
            }
            logger.severe("============================");
        }

        if (!warnings.isEmpty()) {
            logger.warning("=== Configuration Warnings ===");
            for (String warning : warnings) {
                logger.warning("  - " + warning);
            }
            logger.warning("==============================");
        }

        return errors.isEmpty();
    }

    private void cacheValues() {
        int seconds = config.getInt("raid-cooldown-seconds", DEFAULT_COOLDOWN_SECONDS);
        this.cachedCooldownDuration = Duration.ofSeconds(Math.max(0, seconds));
        this.cachedAutoCleanup = config.getBoolean("settings.auto-cleanup", true);
        this.cachedCleanupIntervalMinutes = Math.max(0, config.getInt("settings.cleanup-interval-minutes", DEFAULT_CLEANUP_INTERVAL));
        this.cachedLogActions = config.getBoolean("settings.log-cooldown-actions", true);
        this.cachedSynchronizedResetEnabled = config.getBoolean("synchronized-reset.enabled", false);
        this.cachedResetTime = parseResetTime(config.getString("synchronized-reset.reset-time", "00:00"));
        this.cachedPlaceholderReadyMessage = config.getString("placeholderapi.ready-message", DEFAULT_READY_MESSAGE);
    }

    private LocalTime parseResetTime(String timeString) {
        if (timeString == null || timeString.isBlank()) {
            return DEFAULT_RESET_TIME;
        }
        try {
            return LocalTime.parse(timeString, DateTimeFormatter.ofPattern("H:mm"));
        } catch (DateTimeParseException e) {
            logger.warning("Invalid reset time format '" + timeString + "', using default (00:00)");
            return DEFAULT_RESET_TIME;
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public Duration getCooldownDuration() {
        return cachedCooldownDuration;
    }

    public int getCooldownSeconds() {
        return (int) cachedCooldownDuration.getSeconds();
    }

    public boolean isAutoCleanupEnabled() {
        return cachedAutoCleanup;
    }

    public int getCleanupIntervalMinutes() {
        return cachedCleanupIntervalMinutes;
    }

    public long getCleanupIntervalTicks() {
        if (!cachedAutoCleanup) return 0;
        return cachedCleanupIntervalMinutes * 1200L;
    }

    public boolean shouldLogCooldownActions() {
        return cachedLogActions;
    }

    public boolean isSynchronizedResetEnabled() {
        return cachedSynchronizedResetEnabled;
    }

    public LocalTime getResetTime() {
        return cachedResetTime;
    }

    public String getPlaceholderReadyMessage() {
        return cachedPlaceholderReadyMessage;
    }
}
