package dev.oakheart.raidcooldown.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Manages plugin configuration and cooldown data persistence.
 * <p>
 * Handles two configuration files:
 * <ul>
 *   <li>config.yml - Plugin settings and messages</li>
 *   <li>cooldowns.yml - Persistent cooldown data storage</li>
 * </ul>
 * </p>
 *
 * @author Loralon
 * @version 1.2.0
 */
public class ConfigManager {

    private final JavaPlugin plugin;
    private final Logger logger;

    private FileConfiguration config;
    private File cooldownFile;
    private FileConfiguration cooldownConfig;

    // Cached config values for performance
    private Duration cachedCooldownDuration;
    private boolean cachedAutoCleanup;
    private int cachedCleanupIntervalMinutes;
    private boolean cachedLogActions;

    // Config constants
    private static final String COOLDOWN_SECONDS_KEY = "raidCooldownSeconds";
    private static final int DEFAULT_COOLDOWN_SECONDS = 86400; // 24 hours
    private static final String AUTO_CLEANUP_KEY = "settings.autoCleanup";
    private static final String CLEANUP_INTERVAL_KEY = "settings.cleanupIntervalMinutes";
    private static final String LOG_ACTIONS_KEY = "settings.logCooldownActions";
    private static final int DEFAULT_CLEANUP_INTERVAL = 10; // 10 minutes

    // Required message keys for validation
    private static final String[] REQUIRED_MESSAGE_KEYS = {
        "onlyPlayersMessage",
        "noPermissionMessage",
        "playerNotFoundMessage",
        "reloadMessage",
        "usage",
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

    public ConfigManager(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        initialize();
        validateConfig();
    }

    private void initialize() {
        // Save default config if it doesn't exist
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();

        // Set up cooldown data file
        setupCooldownFile();

        // Cache config values
        cacheConfigValues();
    }

    private void cacheConfigValues() {
        int seconds = config.getInt(COOLDOWN_SECONDS_KEY, DEFAULT_COOLDOWN_SECONDS);
        this.cachedCooldownDuration = Duration.ofSeconds(Math.max(0, seconds));
        this.cachedAutoCleanup = config.getBoolean(AUTO_CLEANUP_KEY, true);
        this.cachedCleanupIntervalMinutes = Math.max(0, config.getInt(CLEANUP_INTERVAL_KEY, DEFAULT_CLEANUP_INTERVAL));
        this.cachedLogActions = config.getBoolean(LOG_ACTIONS_KEY, true);
    }

    private void validateConfig() {
        List<String> warnings = new ArrayList<>();

        // Validate cooldown duration
        int cooldownSeconds = config.getInt(COOLDOWN_SECONDS_KEY, -1);
        if (cooldownSeconds == -1) {
            warnings.add("Missing 'raidCooldownSeconds' - using default: " + DEFAULT_COOLDOWN_SECONDS);
        } else if (cooldownSeconds < 0) {
            warnings.add("Invalid 'raidCooldownSeconds' (negative value) - using 0");
        } else if (cooldownSeconds > 604800) { // 7 days
            warnings.add("'raidCooldownSeconds' is very high (" + cooldownSeconds + "s = " + (cooldownSeconds / 86400) + " days)");
        }

        // Validate cleanup interval
        int cleanupInterval = config.getInt(CLEANUP_INTERVAL_KEY, -1);
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
            if (!config.contains("messages." + key)) {
                missingMessages.add(key);
            }
        }

        if (!missingMessages.isEmpty()) {
            warnings.add("Missing message keys: " + String.join(", ", missingMessages));
        }

        // Log all warnings
        if (!warnings.isEmpty()) {
            logger.warning("=== Configuration Validation Warnings ===");
            for (String warning : warnings) {
                logger.warning("  - " + warning);
            }
            logger.warning("=========================================");
        } else {
            logger.info("Configuration validated successfully");
        }
    }

    private void setupCooldownFile() {
        cooldownFile = new File(plugin.getDataFolder(), "cooldowns.yml");

        if (!cooldownFile.exists()) {
            try {
                if (!cooldownFile.getParentFile().exists()) {
                    cooldownFile.getParentFile().mkdirs();
                }
                cooldownFile.createNewFile();
                logger.info("Created new cooldowns.yml file");
            } catch (IOException e) {
                logger.severe("Could not create cooldowns.yml file: " + e.getMessage());
                throw new RuntimeException("Failed to create cooldown file", e);
            }
        }

        this.cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile);
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        this.cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile);

        // Re-cache config values after reload
        cacheConfigValues();

        // Re-validate after reload
        validateConfig();

        logger.info("Configuration reloaded successfully");
    }

    // Main config getters (using cached values for performance)
    @NotNull
    public Duration getCooldownDuration() {
        return cachedCooldownDuration;
    }

    @NotNull
    public String getMessage(@NotNull String key) {
        return config.getString("messages." + key, "Message not found: " + key);
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
            e.printStackTrace();
        }
    }

    public void reloadCooldownConfig() {
        this.cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile);
    }

    // Validation methods
    public boolean isValidConfig() {
        return config.contains(COOLDOWN_SECONDS_KEY) &&
                config.contains("messages") &&
                cachedCooldownDuration.getSeconds() >= 0;
    }

    // Getters for specific config values (using cached values)
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
        if (!cachedAutoCleanup) {
            return 0; // Disabled
        }
        // Convert minutes to ticks (1 minute = 1200 ticks)
        return cachedCleanupIntervalMinutes * 1200L;
    }

    public boolean shouldLogCooldownActions() {
        return cachedLogActions;
    }
}
