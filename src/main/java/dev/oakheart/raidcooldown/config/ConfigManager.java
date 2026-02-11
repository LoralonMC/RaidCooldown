package dev.oakheart.raidcooldown.config;

import dev.oakheart.raidcooldown.RaidCooldown;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Manages plugin configuration and cooldown data persistence.
 * <p>
 * Handles two configuration files:
 * <ul>
 *   <li>config.yml - Plugin settings and messages</li>
 *   <li>cooldowns.yml - Persistent cooldown data storage</li>
 * </ul>
 */
public class ConfigManager {

    private final RaidCooldown plugin;
    private final Logger logger;
    private final YamlConfigurationLoader loader;
    private final YamlConfigurationLoader cooldownLoader;
    private ConfigurationNode config;
    private ConfigurationNode cooldownConfig;

    // Cached config values for performance
    private Duration cachedCooldownDuration;
    private boolean cachedAutoCleanup;
    private int cachedCleanupIntervalMinutes;
    private boolean cachedLogActions;
    private boolean cachedSynchronizedResetEnabled;
    private LocalTime cachedResetTime;
    private String cachedPlaceholderReadyMessage;

    // Config constants
    private static final int DEFAULT_COOLDOWN_SECONDS = 86400; // 24 hours
    private static final int DEFAULT_CLEANUP_INTERVAL = 10; // 10 minutes
    private static final LocalTime DEFAULT_RESET_TIME = LocalTime.MIDNIGHT;
    private static final String DEFAULT_READY_MESSAGE = "Ready";

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

    public ConfigManager(@NotNull RaidCooldown plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        Path dataFolder = plugin.getDataFolder().toPath();
        this.loader = YamlConfigurationLoader.builder()
                .path(dataFolder.resolve("config.yml"))
                .build();
        this.cooldownLoader = YamlConfigurationLoader.builder()
                .path(dataFolder.resolve("cooldowns.yml"))
                .build();
    }

    /**
     * Initial load of configuration. Called once during onEnable.
     */
    public void load() {
        try {
            Path configPath = plugin.getDataFolder().toPath().resolve("config.yml");
            if (!Files.exists(configPath)) {
                plugin.saveResource("config.yml", false);
            }

            config = loader.load();
            mergeDefaults();
            validateConfig(config);
            cacheConfigValues();

            // Set up cooldown data file
            setupCooldownFile();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration", e);
        }
    }

    /**
     * Merges default config values from the JAR resource into the user's config
     * without overwriting existing values.
     */
    private void mergeDefaults() throws IOException {
        YamlConfigurationLoader defaultLoader = YamlConfigurationLoader.builder()
                .url(getClass().getResource("/config.yml"))
                .build();
        ConfigurationNode defaults = defaultLoader.load();
        config.mergeFrom(defaults);
        loader.save(config);
    }

    private void cacheConfigValues() {
        int seconds = config.node("raidCooldownSeconds").getInt(DEFAULT_COOLDOWN_SECONDS);
        this.cachedCooldownDuration = Duration.ofSeconds(Math.max(0, seconds));
        this.cachedAutoCleanup = config.node("settings", "autoCleanup").getBoolean(true);
        this.cachedCleanupIntervalMinutes = Math.max(0, config.node("settings", "cleanupIntervalMinutes").getInt(DEFAULT_CLEANUP_INTERVAL));
        this.cachedLogActions = config.node("settings", "logCooldownActions").getBoolean(true);
        this.cachedSynchronizedResetEnabled = config.node("synchronizedReset", "enabled").getBoolean(false);
        this.cachedResetTime = parseResetTime(config.node("synchronizedReset", "resetTime").getString("00:00"));
        this.cachedPlaceholderReadyMessage = config.node("placeholderapi", "readyMessage").getString(DEFAULT_READY_MESSAGE);
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

    private boolean validateConfig(ConfigurationNode configToValidate) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Validate cooldown duration
        int cooldownSeconds = configToValidate.node("raidCooldownSeconds").getInt(-1);
        if (cooldownSeconds == -1) {
            warnings.add("Missing 'raidCooldownSeconds' - using default: " + DEFAULT_COOLDOWN_SECONDS);
        } else if (cooldownSeconds < 0) {
            errors.add("Invalid 'raidCooldownSeconds': " + cooldownSeconds + " (must be non-negative)");
        } else if (cooldownSeconds > 604800) { // 7 days
            warnings.add("'raidCooldownSeconds' is very high (" + cooldownSeconds + "s = " + (cooldownSeconds / 86400) + " days)");
        }

        // Validate cleanup interval
        int cleanupInterval = configToValidate.node("settings", "cleanupIntervalMinutes").getInt(-1);
        if (cleanupInterval < 0) {
            warnings.add("Invalid 'cleanupIntervalMinutes' (negative value) - using default: " + DEFAULT_CLEANUP_INTERVAL);
        } else if (cleanupInterval == 0) {
            logger.info("Auto cleanup is configured but interval is 0 - cleanup will be disabled");
        } else if (cleanupInterval < 5) {
            warnings.add("'cleanupIntervalMinutes' is very low (" + cleanupInterval + " minutes) - may impact performance");
        }

        // Validate required messages
        List<String> missingMessages = new ArrayList<>();
        ConfigurationNode messagesNode = configToValidate.node("messages");
        for (String key : REQUIRED_MESSAGE_KEYS) {
            if (messagesNode.node(key).virtual()) {
                missingMessages.add(key);
            }
        }

        if (!missingMessages.isEmpty()) {
            warnings.add("Missing message keys: " + String.join(", ", missingMessages));
        }

        // Validate synchronized reset settings
        if (configToValidate.node("synchronizedReset", "enabled").getBoolean(false)) {
            String resetTimeStr = configToValidate.node("synchronizedReset", "resetTime").getString("00:00");
            try {
                LocalTime parsed = LocalTime.parse(resetTimeStr, DateTimeFormatter.ofPattern("H:mm"));
                logger.info("Synchronized reset enabled - cooldowns will reset daily at " + parsed);
            } catch (DateTimeParseException e) {
                warnings.add("Invalid reset time format '" + resetTimeStr + "' - using default (00:00)");
            }
        }

        // Log errors
        if (!errors.isEmpty()) {
            logger.severe("=== Configuration Errors ===");
            for (String error : errors) {
                logger.severe("  - " + error);
            }
            logger.severe("============================");
        }

        // Log warnings
        if (!warnings.isEmpty()) {
            logger.warning("=== Configuration Validation Warnings ===");
            for (String warning : warnings) {
                logger.warning("  - " + warning);
            }
            logger.warning("=========================================");
        }

        if (errors.isEmpty() && warnings.isEmpty()) {
            logger.info("Configuration validated successfully");
        }

        return errors.isEmpty();
    }

    private void setupCooldownFile() throws IOException {
        Path cooldownPath = plugin.getDataFolder().toPath().resolve("cooldowns.yml");
        if (!Files.exists(cooldownPath)) {
            Files.createDirectories(cooldownPath.getParent());
            Files.createFile(cooldownPath);
            logger.info("Created new cooldowns.yml file");
        }

        this.cooldownConfig = cooldownLoader.load();
    }

    /**
     * Reloads configuration from disk. Validates before applying.
     *
     * @return true if reload was successful
     */
    public boolean reload() {
        try {
            ConfigurationNode newConfig = loader.load();

            if (!validateConfig(newConfig)) {
                logger.warning("Configuration reload failed validation. Keeping previous configuration.");
                return false;
            }

            this.config = newConfig;
            this.cooldownConfig = cooldownLoader.load();
            cacheConfigValues();

            logger.info("Configuration reloaded successfully.");
            return true;
        } catch (IOException e) {
            logger.warning("Failed to reload configuration: " + e.getMessage());
            return false;
        }
    }

    // Main config getters (using cached values for performance)
    @NotNull
    public Duration getCooldownDuration() {
        return cachedCooldownDuration;
    }

    @NotNull
    public String getMessage(@NotNull String key) {
        return config.node("messages", key).getString("Message not found: " + key);
    }

    @NotNull
    public String getMessage(@NotNull String key, @NotNull String defaultValue) {
        return config.node("messages", key).getString(defaultValue);
    }

    // Cooldown data file operations
    @NotNull
    public ConfigurationNode getCooldownConfig() {
        return cooldownConfig;
    }

    public void saveCooldownConfig() {
        try {
            cooldownLoader.save(cooldownConfig);
        } catch (IOException e) {
            logger.severe("Could not save cooldown data: " + e.getMessage());
        }
    }

    public void reloadCooldownConfig() {
        try {
            this.cooldownConfig = cooldownLoader.load();
        } catch (IOException e) {
            logger.warning("Failed to reload cooldown data: " + e.getMessage());
        }
    }

    // Validation methods
    public boolean isValidConfig() {
        return config != null &&
                !config.node("raidCooldownSeconds").virtual() &&
                !config.node("messages").virtual();
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
