package dev.oakheart.raidcooldown.config;

import dev.oakheart.raidcooldown.RaidCooldown;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

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

/**
 * Manages plugin configuration and cooldown data persistence.
 */
public class ConfigManager {

    private final RaidCooldown plugin;
    private final Logger logger;
    private final File configFile;
    private final File cooldownFile;
    private FileConfiguration config;
    private FileConfiguration cooldownConfig;

    // Cached config values
    private Duration cachedCooldownDuration;
    private boolean cachedAutoCleanup;
    private int cachedCleanupIntervalMinutes;
    private boolean cachedLogActions;
    private boolean cachedSynchronizedResetEnabled;
    private LocalTime cachedResetTime;
    private String cachedPlaceholderReadyMessage;

    // Config constants
    private static final int DEFAULT_COOLDOWN_SECONDS = 86400; // 24 hours
    private static final int DEFAULT_CLEANUP_INTERVAL = 10;
    private static final LocalTime DEFAULT_RESET_TIME = LocalTime.MIDNIGHT;
    private static final String DEFAULT_READY_MESSAGE = "Ready";

    // Required message keys for validation
    private static final String[] REQUIRED_MESSAGE_KEYS = {
        "onlyPlayersMessage",
        "playerNotFoundMessage",
        "reloadMessage",
        "raidCooldownMessage",
        "cooldownRemainingMessage",
        "cooldownRemainingOtherMessage",
        "raidAvailableMessage",
        "raidAvailableOtherMessage",
        "resetCooldownMessage",
        "cooldownResetNotification",
        "reloadError",
        "infoHeader",
        "infoActiveCooldowns",
        "infoCooldownDuration",
        "infoConfigValid"
    };

    public ConfigManager(@NotNull RaidCooldown plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.cooldownFile = new File(plugin.getDataFolder(), "cooldowns.yml");
    }

    /**
     * Initial load of configuration. Called once during onEnable.
     */
    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        if (!validateConfig(config)) {
            throw new RuntimeException("Configuration validation failed");
        }

        mergeDefaults();
        cacheConfigValues();
        setupCooldownFile();
    }

    /**
     * Reloads configuration from disk. Validates before applying.
     *
     * @return true if reload was successful
     */
    public boolean reload() {
        FileConfiguration newConfig = YamlConfiguration.loadConfiguration(configFile);

        if (!validateConfig(newConfig)) {
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

        this.cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile);
        cacheConfigValues();
        logger.info("Configuration reloaded successfully.");
        return true;
    }

    /**
     * Sets JAR defaults as fallback values in memory. Only saves to disk
     * if new keys were added, to avoid reformatting the user's config file.
     */
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

    private void cacheConfigValues() {
        int seconds = config.getInt("raidCooldownSeconds", DEFAULT_COOLDOWN_SECONDS);
        this.cachedCooldownDuration = Duration.ofSeconds(Math.max(0, seconds));
        this.cachedAutoCleanup = config.getBoolean("settings.autoCleanup", true);
        this.cachedCleanupIntervalMinutes = Math.max(0, config.getInt("settings.cleanupIntervalMinutes", DEFAULT_CLEANUP_INTERVAL));
        this.cachedLogActions = config.getBoolean("settings.logCooldownActions", true);
        this.cachedSynchronizedResetEnabled = config.getBoolean("synchronizedReset.enabled", false);
        this.cachedResetTime = parseResetTime(config.getString("synchronizedReset.resetTime", "00:00"));
        this.cachedPlaceholderReadyMessage = config.getString("placeholderapi.readyMessage", DEFAULT_READY_MESSAGE);
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

    private boolean validateConfig(FileConfiguration configToValidate) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Validate cooldown duration
        if (!configToValidate.contains("raidCooldownSeconds")) {
            warnings.add("Missing 'raidCooldownSeconds' - using default: " + DEFAULT_COOLDOWN_SECONDS);
        } else {
            int cooldownSeconds = configToValidate.getInt("raidCooldownSeconds", DEFAULT_COOLDOWN_SECONDS);
            if (cooldownSeconds < 0) {
                errors.add("Invalid 'raidCooldownSeconds': " + cooldownSeconds + " (must be non-negative)");
            } else if (cooldownSeconds > 604800) {
                warnings.add("'raidCooldownSeconds' is very high (" + cooldownSeconds + "s = " + (cooldownSeconds / 86400) + " days)");
            }
        }

        // Validate cleanup interval
        int cleanupInterval = configToValidate.getInt("settings.cleanupIntervalMinutes", DEFAULT_CLEANUP_INTERVAL);
        if (cleanupInterval < 0) {
            warnings.add("Invalid 'cleanupIntervalMinutes' (negative value) - using default: " + DEFAULT_CLEANUP_INTERVAL);
        } else if (cleanupInterval == 0) {
            logger.info("Auto cleanup is configured but interval is 0 - cleanup will be disabled");
        } else if (cleanupInterval < 5) {
            warnings.add("'cleanupIntervalMinutes' is very low (" + cleanupInterval + " minutes) - may impact performance");
        }

        // Validate required messages
        List<String> missingMessages = new ArrayList<>();
        for (String key : REQUIRED_MESSAGE_KEYS) {
            if (!configToValidate.contains("messages." + key)) {
                missingMessages.add(key);
            }
        }
        if (!missingMessages.isEmpty()) {
            warnings.add("Missing message keys: " + String.join(", ", missingMessages));
        }

        // Validate synchronized reset settings
        if (configToValidate.getBoolean("synchronizedReset.enabled", false)) {
            String resetTimeStr = configToValidate.getString("synchronizedReset.resetTime", "00:00");
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

        if (errors.isEmpty() && warnings.isEmpty()) {
            logger.info("Configuration validated successfully");
        }

        return errors.isEmpty();
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

    // Main config getters
    @NotNull
    public Duration getCooldownDuration() {
        return cachedCooldownDuration;
    }

    @NotNull
    public String getMessage(@NotNull String key) {
        return config.getString("messages." + key, "");
    }

    @NotNull
    public String getMessage(@NotNull String key, @NotNull String defaultValue) {
        return config.getString("messages." + key, defaultValue);
    }

    // Cooldown data file operations
    @NotNull
    public FileConfiguration getCooldownConfig() {
        return cooldownConfig;
    }

    public void saveCooldownConfig() {
        try {
            cooldownConfig.save(cooldownFile);
        } catch (IOException e) {
            logger.severe("Could not save cooldown data: " + e.getMessage());
        }
    }

    public boolean isValidConfig() {
        return config != null && config.contains("raidCooldownSeconds") && config.contains("messages");
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

    @NotNull
    public LocalTime getResetTime() {
        return cachedResetTime;
    }

    @NotNull
    public String getPlaceholderReadyMessage() {
        return cachedPlaceholderReadyMessage;
    }
}
